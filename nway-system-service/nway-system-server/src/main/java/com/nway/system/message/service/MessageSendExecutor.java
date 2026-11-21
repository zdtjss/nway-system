package com.nway.system.message.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.message.support.channel.MessageChannelSender;
import com.nway.system.message.domain.enums.MessageSendStatusEnum;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.message.dao.MessageReceiverDao;
import com.nway.system.message.dao.MessageSendLogDao;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.message.entity.MessageReceiver;
import com.nway.system.message.entity.MessageSendLog;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息发送执行器：异步发送各渠道并记录发送日志，支持失败重试
 * <p>
 * 独立为单独 Service，确保 {@link Async} 通过 Spring 代理生效（同类内部调用不会触发异步）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSendExecutor {

    private final List<MessageChannelSender> channelSenders;
    private final MessageSendLogDao messageSendLogDao;
    private final MessageReceiverDao messageReceiverDao;
    private final UserDao userDao;

    private static final int MAX_RETRY = 3;

    /**
     * 异步发送：按渠道、按接收人逐条发送并记录日志
     * <p>
     * 异步线程不继承请求线程的 ThreadLocal 上下文，租户由调用方显式传入
     * （否则租户隔离失效且发送日志会被填充为默认租户，导致数据归属错误）。
     *
     * @param tenantCode 消息所属租户（调用线程上下文中的租户）
     */
    @Async("messageTaskExecutor")
    public void send(MessageInfo message, List<MessageReceiver> receivers, List<String> channels, String tenantCode) {
        String scopedTenant = StringUtils.isNotBlank(tenantCode) ? tenantCode : message.getTenantCode();
        if (StringUtils.isBlank(scopedTenant)) {
            log.warn("消息 {} 缺少租户信息，按最小范围（public）执行发送", message.getId());
        }
        TenantUtil.runAsTenant(scopedTenant, () -> doSend(message, receivers, channels, scopedTenant));
    }

    private void doSend(MessageInfo message, List<MessageReceiver> receivers, List<String> channels, String tenantCode) {
        // 接收人可能跨租户（管理员代发）；按发送人租户隔离查询会漏人，需系统级跨租户取用户信息
        List<User> users = com.nway.system.common.tenant.TenantUtil.callAsSystem(
                () -> userDao.getByEmpids(receivers.stream().map(MessageReceiver::getUserId).toList()));
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getEmployeeId, Function.identity()));
        List<MessageSendLog> logs = new ArrayList<>();

        for (String channel : channels) {
            MessageChannelSender sender = channelSenders.stream()
                    .filter(s -> s.getChannel().equals(channel))
                    .findFirst()
                    .orElse(null);
            if (sender == null) {
                log.warn("未找到渠道 {} 的发送器，跳过", channel);
                continue;
            }
            for (MessageReceiver receiver : receivers) {
                logs.add(doSendPerReceiver(message, receiver, channel, sender, userMap, tenantCode));
            }
        }
        if (!logs.isEmpty()) {
            messageSendLogDao.insertBatch(logs);
            updateReceiverChannelStatus(receivers, logs);
        }
    }

    /**
     * 重试单条失败日志
     */
    public void retry(MessageSendLog logEntry, MessageInfo message, User user) {
        MessageChannelSender sender = channelSenders.stream()
                .filter(s -> s.getChannel().equals(logEntry.getChannel()))
                .findFirst()
                .orElse(null);
        if (sender == null) {
            log.warn("重试：未找到渠道 {} 发送器", logEntry.getChannel());
            return;
        }
        try {
            sender.send(message, List.of(user));
            logEntry.setStatus(MessageSendStatusEnum.SUCCESS.getCode());
            logEntry.setErrorMsg(null);
            logEntry.setSendTime(LocalDateTime.now());
        } catch (Exception e) {
            log.error("重试发送失败 log={}", logEntry.getId(), e);
            int newRetry = (logEntry.getRetryCount() == null ? 0 : logEntry.getRetryCount()) + 1;
            logEntry.setRetryCount(newRetry);
            logEntry.setErrorMsg(StringUtils.left(e.getMessage(), 1000));
            logEntry.setSendTime(LocalDateTime.now());
            logEntry.setStatus(MessageSendStatusEnum.FAILED.getCode());
            if (newRetry >= MAX_RETRY) {
                logEntry.setNextRetryTime(null);
            } else {
                logEntry.setNextRetryTime(LocalDateTime.now().plusMinutes((long) newRetry * 2));
            }
        }
        messageSendLogDao.updateBatchById(List.of(logEntry));
    }

    private MessageSendLog doSendPerReceiver(MessageInfo message, MessageReceiver receiver, String channel,
                                             MessageChannelSender sender, Map<String, User> userMap, String tenantCode) {
        MessageSendLog logEntry = new MessageSendLog();
        // 显式落库消息所属租户，避免依赖上下文填充导致归属错误
        logEntry.setTenantCode(StringUtils.isNotBlank(tenantCode) ? tenantCode : message.getTenantCode());
        logEntry.setMessageId(message.getId());
        logEntry.setReceiverId(receiver.getId());
        logEntry.setUserId(receiver.getUserId());
        logEntry.setChannel(channel);
        logEntry.setRetryCount(0);
        logEntry.setMaxRetry(MAX_RETRY);
        try {
            User user = userMap.get(receiver.getUserId());
            if (user != null) {
                sender.send(message, List.of(user));
            }
            logEntry.setStatus(MessageSendStatusEnum.SUCCESS.getCode());
            logEntry.setSendTime(LocalDateTime.now());
        } catch (Exception e) {
            log.error("渠道 {} 发送失败，消息 {} 接收人 {}", channel, message.getId(), receiver.getUserId(), e);
            logEntry.setStatus(MessageSendStatusEnum.FAILED.getCode());
            logEntry.setErrorMsg(StringUtils.left(e.getMessage(), 1000));
            logEntry.setSendTime(LocalDateTime.now());
            logEntry.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
        }
        return logEntry;
    }

    /**
     * 根据发送日志更新接收人明细的 channelStatus JSON
     */
    @SuppressWarnings("unchecked")
    private void updateReceiverChannelStatus(List<MessageReceiver> receivers, List<MessageSendLog> logs) {
        Map<String, MessageReceiver> receiverMap = receivers.stream()
                .collect(Collectors.toMap(MessageReceiver::getId, Function.identity()));
        Map<String, Map<String, Integer>> statusByReceiver = new HashMap<>();
        for (MessageSendLog logEntry : logs) {
            Map<String, Integer> sm = statusByReceiver.computeIfAbsent(logEntry.getReceiverId(), k -> new HashMap<>());
            int val = MessageSendStatusEnum.SUCCESS.getCode().equals(logEntry.getStatus()) ? 1 : 0;
            sm.put(logEntry.getChannel(), val);
        }
        List<MessageReceiver> toUpdate = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : statusByReceiver.entrySet()) {
            MessageReceiver r = receiverMap.get(e.getKey());
            if (r != null) {
                Map<String, Integer> existing = StringUtils.isNotBlank(r.getChannelStatus())
                        ? JSON.parseObject(r.getChannelStatus(), Map.class)
                        : new HashMap<>();
                existing.putAll(e.getValue());
                r.setChannelStatus(JSON.toJSONString(existing));
                toUpdate.add(r);
            }
        }
        if (!toUpdate.isEmpty()) {
            messageReceiverDao.updateBatchById(toUpdate);
        }
    }
}
