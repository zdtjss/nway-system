package com.nway.system.message.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.message.support.resolver.MessageTargetResolver;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.message.dao.MessageInfoDao;
import com.nway.system.message.dao.MessageReceiverDao;
import com.nway.system.message.dao.MessageTargetDao;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.message.entity.MessageReceiver;
import com.nway.system.message.entity.MessageTarget;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.message.api.SendMessageDto;
import com.nway.system.message.web.model.MessageQuery;
import com.nway.system.message.web.model.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageInfoDao messageInfoDao;
    private final MessageReceiverDao messageReceiverDao;
    private final MessageTargetDao messageTargetDao;
    private final List<MessageTargetResolver> targetResolvers;
    private final MessageSendExecutor messageSendExecutor;
    private final UserDao userDao;

    @Transactional(rollbackFor = Exception.class)
    public String sendMessage(SendMessageDto messageReq) {
        List<String> channels = messageReq.getChannels();
        if (CollectionUtils.isEmpty(channels)) {
            throw new NwaySystemException("发送渠道不能为空");
        }
        if (CollectionUtils.isEmpty(messageReq.getTargets())) {
            throw new NwaySystemException("消息目标不能为空");
        }

        // 1. 落库消息主体
        MessageInfo message = ConverterUtil.convert(messageReq, MessageInfo.class);
        message.setSendTime(LocalDateTime.now());
        messageInfoDao.insert(message);

        // 2. 落库目标
        List<MessageTarget> targetList = ConverterUtil.convert2(messageReq.getTargets(), MessageTarget.class,
                (src, t) -> t.setMessageId(message.getId()));
        messageTargetDao.insertBatch(targetList);

        // 3. 解析受众为用户工号
        Set<String> userIds = resolveUserIds(targetList);
        if (userIds.isEmpty()) {
            throw new NwaySystemException("未找到有效接收人");
        }

        // 4. 落库接收人明细（消除双写，仅持久化 JSON 字符串）
        String channelsJson = JSON.toJSONString(channels);
        Map<String, Integer> initStatus = channels.stream()
                .collect(Collectors.toMap(Function.identity(), c -> 0));
        String channelStatusJson = JSON.toJSONString(initStatus);

        // 接收明细的租户归属必须是“接收人所属租户”（而非发送人的当前租户）：
        // 否则管理员跨租户发消息时，接收人在自己的租户上下文里被隔离过滤，永远收不到。
        // 用户查询跳出租户隔离（callAsSystem）：目的就是拿到其他租户接收人的归属。
        Map<String, String> userTenantMap = TenantUtil.callAsSystem(() ->
                userDao.getByEmpids(userIds).stream()
                        .collect(Collectors.toMap(u -> u.getEmployeeId(),
                                u -> StringUtils.defaultIfBlank(u.getTenantCode(),
                                        TenantUtil.currentTenantCode()))));
        String senderTenant = TenantUtil.currentTenantCode();

        List<MessageReceiver> receivers = userIds.stream().map(userId -> {
            MessageReceiver r = new MessageReceiver();
            r.setMessageId(message.getId());
            r.setUserId(userId);
            // 显式指定租户，避免 nway-jdbc 按发送人上下文自动填充（跨租户不可达）
            r.setTenantCode(StringUtils.defaultIfBlank(userTenantMap.get(userId), senderTenant));
            r.setIsRead(false);
            r.setIsNew(true);
            r.setChannels(channelsJson);
            r.setChannelStatus(channelStatusJson);
            return r;
        }).toList();
        messageReceiverDao.insertBatch(receivers);

        // 5. 异步发送各渠道并记录日志（显式传递租户，异步线程不继承 ThreadLocal 上下文）
        messageSendExecutor.send(message, receivers, channels, TenantUtil.currentTenantCode());

        return message.getId();
    }

    private Set<String> resolveUserIds(List<MessageTarget> targets) {
        Map<Integer, List<MessageTarget>> grouped = targets.stream()
                .collect(Collectors.groupingBy(MessageTarget::getTargetType));
        Set<String> userIds = new HashSet<>();
        for (Map.Entry<Integer, List<MessageTarget>> entry : grouped.entrySet()) {
            MessageTargetResolver resolver = targetResolvers.stream()
                    .filter(r -> r.getTargetType() == entry.getKey())
                    .findFirst()
                    .orElseThrow(() -> new NwaySystemException("不支持的目标类型: " + entry.getKey()));
            userIds.addAll(resolver.resolveUserIds(entry.getValue()));
        }
        return userIds;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(String receiveId) {
        LoginUser currentUser = LoginUserContext.current();
        MessageReceiver receiver = messageReceiverDao.selectById(receiveId);
        if (receiver == null) {
            throw new NwaySystemException("消息不存在");
        }
        if (!currentUser.getEmployeeId().equals(receiver.getUserId())) {
            throw new NwaySystemException("无权操作他人消息");
        }
        messageReceiverDao.markAsRead(receiveId);
    }

    @SuppressWarnings("unchecked")
    public List<MessageVO> getPersonalMessages(MessageQuery query) {
        if (query.getCreateTimeBegin() == null) {
            query.setCreateTimeBegin(LocalDate.now().minusDays(7).atStartOfDay());
        }
        if (query.getCreateTimeEnd() == null) {
            query.setCreateTimeEnd(LocalDateTime.now());
        }

        LoginUser user = LoginUserContext.current();
        // 以“我的接收记录”为准（receiver 归属接收人租户，跨租户发送也能命中），
        // 再按 messageId 取消息主体；主体归属发送人租户，必须跳出租户隔离（仅按主键取内容）。
        List<MessageReceiver> messageReceivers = messageReceiverDao.queryByUserAndTime(
                user.getEmployeeId(), query.getCreateTimeBegin(), query.getCreateTimeEnd());
        if (CollectionUtils.isEmpty(messageReceivers)) {
            return List.of();
        }
        List<String> messageIdList = messageReceivers.stream()
                .map(MessageReceiver::getMessageId).distinct().toList();
        Map<String, MessageInfo> messageInfoMap = TenantUtil.callAsSystem(
                () -> messageInfoDao.queryByIds(messageIdList));

        LocalDateTime now = LocalDateTime.now();
        List<MessageVO> messageVOList = new ArrayList<>(messageReceivers.size());
        for (MessageReceiver receiver : messageReceivers) {
            MessageInfo message = messageInfoMap.get(receiver.getMessageId());
            if (message == null) {
                continue;
            }
            // 保留查询条件的兼容过滤（标题/正文/发送人）
            if (StringUtils.isNotBlank(query.getTitle())
                    && !StringUtils.containsIgnoreCase(message.getTitle(), query.getTitle())) {
                continue;
            }
            if (StringUtils.isNotBlank(query.getContent())
                    && !StringUtils.containsIgnoreCase(message.getContent(), query.getContent())) {
                continue;
            }
            if (StringUtils.isNotBlank(query.getSenderId())
                    && !query.getSenderId().equals(message.getSenderId())) {
                continue;
            }
            // 过滤过期消息
            if (message.getExpireTime() != null && message.getExpireTime().isBefore(now)) {
                continue;
            }
            MessageVO vo = ConverterUtil.convert(message, MessageVO.class);
            vo.setIsRead(receiver.getIsRead());
            vo.setReadTime(receiver.getReadTime());
            vo.setReceiverId(receiver.getId());
            if (StringUtils.isNotBlank(receiver.getChannels())) {
                vo.setChannels(JSON.parseArray(receiver.getChannels(), String.class));
            }
            if (StringUtils.isNotBlank(receiver.getChannelStatus())) {
                vo.setChannelStatus(JSON.parseObject(receiver.getChannelStatus(), Map.class));
            }
            messageVOList.add(vo);
        }
        return messageVOList;
    }
}
