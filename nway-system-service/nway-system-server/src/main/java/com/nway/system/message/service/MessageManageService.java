package com.nway.system.message.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.web.PageVo;
import com.nway.system.message.domain.enums.MessageSendStatusEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.message.dao.MessageInfoDao;
import com.nway.system.message.dao.MessageReceiverDao;
import com.nway.system.message.dao.MessageSendLogDao;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.message.entity.MessageReceiver;
import com.nway.system.message.entity.MessageSendLog;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import com.nway.system.message.web.model.MessageQuery;
import com.nway.system.message.web.model.MessageManageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.nway.spring.jdbc.pagination.Page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息管理后台服务：发送列表（含接收人/已读统计）与发送日志查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageManageService {

    private final MessageInfoDao messageInfoDao;
    private final MessageReceiverDao messageReceiverDao;
    private final MessageSendLogDao messageSendLogDao;
    private final TenantDao tenantDao;
    private final UserDao userDao;
    private final MessageSendExecutor messageSendExecutor;

    /**
     * 分页查询消息发送列表，附带接收人数与已读数统计
     */
    public PageVo<MessageManageVO> getManageList(MessageQuery query) {
        Page<MessageInfo> page = messageInfoDao.queryPage(query);
        if (page == null || CollectionUtils.isEmpty(page.getPageData())) {
            return new PageVo<>(Collections.emptyList(), 0L);
        }

        List<MessageInfo> messageList = page.getPageData();
        List<String> messageIds = ConverterUtil.extract(messageList, MessageInfo::getId);

        // 批量查询接收人明细，按 messageId 分组统计
        List<MessageReceiver> receivers = messageReceiverDao.queryByMessageIds(messageIds);
        Map<String, List<MessageReceiver>> receiverGroup = receivers.stream()
                .collect(Collectors.groupingBy(MessageReceiver::getMessageId));

        List<MessageManageVO> voList = new ArrayList<>(messageList.size());
        for (MessageInfo message : messageList) {
            MessageManageVO vo = ConverterUtil.convert(message, MessageManageVO.class);
            // MessageInfo 主键为 id，VO 字段名为 messageId，需显式赋值
            vo.setMessageId(message.getId());
            List<MessageReceiver> group = receiverGroup.getOrDefault(message.getId(), Collections.emptyList());
            vo.setReceiverCount(group.size());
            vo.setReadCount((int) group.stream().filter(r -> Boolean.TRUE.equals(r.getIsRead())).count());
            // channels 取第一条接收人的渠道列表（同一消息所有接收人渠道一致）
            if (!group.isEmpty() && StringUtils.isNotBlank(group.get(0).getChannels())) {
                vo.setChannels(JSON.parseArray(group.get(0).getChannels(), String.class));
            }
            voList.add(vo);
        }

        PageVo<MessageManageVO> result = new PageVo<>();
        result.setTotal(page.getTotal());
        result.setData(voList);
        // 填充租户名称
        fillTenantNames(voList);
        return result;
    }

    /**
     * 查询指定消息的发送日志（按渠道、按接收人记录的发送结果）
     */
    public List<MessageSendLog> getSendLogs(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return Collections.emptyList();
        }
        return messageSendLogDao.queryByMessage(messageId);
    }

    /**
     * 手动重发：重发指定消息、接收人、渠道的失败发送记录
     * <p>仅允许重发当前登录用户自己的接收记录（个人收件箱场景）
     */
    public void resend(String messageId, String receiverId, String channel) {
        if (StringUtils.isAnyBlank(messageId, receiverId, channel)) {
            throw new NwaySystemException("参数不完整");
        }
        // 校验接收记录归属当前登录用户
        LoginUser currentUser = LoginUserContext.current();
        MessageReceiver receiver = messageReceiverDao.selectById(receiverId);
        if (receiver == null || !currentUser.getEmployeeId().equals(receiver.getUserId())) {
            throw new NwaySystemException("无权重发该消息");
        }

        MessageSendLog logEntry = messageSendLogDao.queryByReceiverChannel(messageId, receiverId, channel);
        if (logEntry == null) {
            throw new NwaySystemException("发送记录不存在");
        }
        if (!MessageSendStatusEnum.FAILED.getCode().equals(logEntry.getStatus())) {
            throw new NwaySystemException("该渠道当前非失败状态，无需重发");
        }

        MessageInfo message = messageInfoDao.queryByIds(List.of(messageId)).get(messageId);
        User user = userDao.getByEmployeeId(receiver.getUserId());
        if (message == null || user == null) {
            throw new NwaySystemException("消息或接收人不存在，无法重发");
        }

        messageSendExecutor.retry(logEntry, message, user);
        if (MessageSendStatusEnum.SUCCESS.getCode().equals(logEntry.getStatus())) {
            // 同步接收人渠道状态，前端展示为重发成功
            updateReceiverChannelStatus(logEntry);
        } else {
            throw new NwaySystemException("重发失败：" + StringUtils.defaultString(logEntry.getErrorMsg(), "未知原因"));
        }
    }

    /**
     * 重发成功后，将接收人渠道状态置为成功
     */
    @SuppressWarnings("unchecked")
    private void updateReceiverChannelStatus(MessageSendLog logEntry) {
        MessageReceiver receiver = messageReceiverDao.selectById(logEntry.getReceiverId());
        if (receiver == null) {
            return;
        }
        Map<String, Integer> status = StringUtils.isNotBlank(receiver.getChannelStatus())
                ? JSON.parseObject(receiver.getChannelStatus(), Map.class)
                : new HashMap<>();
        status.put(logEntry.getChannel(), 1);
        receiver.setChannelStatus(JSON.toJSONString(status));
        messageReceiverDao.updateBatchById(List.of(receiver));
    }

    private void fillTenantNames(List<MessageManageVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<String> tenantCodes = list.stream()
                .map(MessageManageVO::getTenantCode)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (tenantCodes.isEmpty()) {
            return;
        }
        Map<String, String> nameMap = new HashMap<>();
        for (Tenant t : tenantDao.queryByTenantCode(tenantCodes)) {
            nameMap.put(t.getTenantCode(), t.getTenantName());
        }
        for (MessageManageVO vo : list) {
            if (StringUtils.isNotBlank(vo.getTenantCode())) {
                vo.setTenantName(nameMap.get(vo.getTenantCode()));
            }
        }
    }
}
