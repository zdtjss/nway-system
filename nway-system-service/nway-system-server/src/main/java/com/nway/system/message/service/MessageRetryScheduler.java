package com.nway.system.message.service;

import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.message.dao.MessageInfoDao;
import com.nway.system.message.dao.MessageSendLogDao;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.message.entity.MessageSendLog;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息发送失败重试调度器：定时扫描 failed 记录并重试
 * <p>
 * 这是显式声明的系统级跨租户任务：全程运行在 {@link TenantUtil#callAsSystem} 上下文中，
 * 便于审计与回溯（不再依赖"无登录上下文 = 全域放行"的隐式行为）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRetryScheduler {

    /** 单次扫描上限，避免一次拉取过多记录占用内存与连接 */
    private static final int SCAN_LIMIT = 500;

    private final MessageSendLogDao messageSendLogDao;
    private final MessageInfoDao messageInfoDao;
    private final UserDao userDao;
    private final MessageSendExecutor messageSendExecutor;

    /**
     * 每 60 秒扫描一次失败记录并重试
     */
    @Scheduled(fixedDelay = 60_000L)
    public void retryFailed() {
        TenantUtil.callAsSystem(this::doRetryFailed);
    }

    private Object doRetryFailed() {
        List<MessageSendLog> candidates = messageSendLogDao.queryRetryCandidates(LocalDateTime.now(), SCAN_LIMIT);
        if (candidates.isEmpty()) {
            return null;
        }
        log.info("扫描到 {} 条失败消息待重试", candidates.size());

        Set<String> messageIds = candidates.stream()
                .map(MessageSendLog::getMessageId)
                .collect(Collectors.toSet());
        Map<String, MessageInfo> messageMap = messageInfoDao.queryByIds(messageIds);

        Set<String> userIds = candidates.stream()
                .map(MessageSendLog::getUserId)
                .collect(Collectors.toSet());
        List<User> users = userDao.getByEmpids(userIds);
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getEmployeeId, Function.identity()));

        for (MessageSendLog logEntry : candidates) {
            MessageInfo message = messageMap.get(logEntry.getMessageId());
            User user = userMap.get(logEntry.getUserId());
            if (message == null || user == null) {
                continue;
            }
            try {
                // 以该条记录所属消息的租户执行重试，保持单条重试的租户收敛
                TenantUtil.runAsTenant(logEntry.getTenantCode(),  () -> messageSendExecutor.retry(logEntry, message, user));
            } catch (Exception e) {
                log.error("重试异常 log={}", logEntry.getId(), e);
            }
        }
        return null;
    }
}
