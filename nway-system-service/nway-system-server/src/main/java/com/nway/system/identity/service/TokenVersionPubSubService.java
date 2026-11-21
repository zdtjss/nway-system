package com.nway.system.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Token 版本号变更的 Redis Pub/Sub 服务。
 * <p>
 * 解决多实例部署场景下的问题：强制下线后，各实例本地 Caffeine 缓存的
 * token 版本号最长 30 秒才过期刷新。通过 Pub/Sub 推送变更通知，收到消息后
 * 各实例立即 invalidate 本地缓存，将下线延迟从 30s 降至毫秒级。
 * <p>
 * 消息格式：{@code employeeId:action}，其中 action 为 {@code disable} 或 {@code logout}。
 * 兼容旧格式（无冒号的纯 employeeId），按 disable 处理。
 * <p>
 * 容错：Redis Pub/Sub 不保证送达（订阅方掉线期间的消息会丢失），
 * 但这是可接受的——Caffeine 30s TTL 作为兜底保障，最坏情况仍为 30s 而非永不失效。
 *
 * @author nway
 */
@Slf4j
@Component
public class TokenVersionPubSubService implements MessageListener {

    private static final String CHANNEL = "nway:token_version_changed";

    /** 操作类型：用户被禁用 */
    public static final String ACTION_DISABLE = "disable";
    /** 操作类型：强制下线（改密码等），用户仍为正常状态 */
    public static final String ACTION_LOGOUT = "logout";
    /** 操作类型：用户被启用，需清除禁用标记 */
    public static final String ACTION_ENABLE = "enable";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private UserStatusCache userStatusCache;

    @PostConstruct
    public void subscribe() {
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("已订阅 Token 版本号变更通道: {}", CHANNEL);
    }

    /**
     * 发布 Token 版本号变更通知（用户被禁用场景）。
     *
     * @param employeeId 版本号变更的用户工号
     */
    public void publishVersionChange(String employeeId) {
        publishVersionChange(employeeId, ACTION_DISABLE);
    }

    /**
     * 发布 Token 版本号变更通知（指定操作类型）。
     *
     * @param employeeId 版本号变更的用户工号
     * @param action     操作类型：{@link #ACTION_DISABLE} / {@link #ACTION_LOGOUT} / {@link #ACTION_ENABLE}
     */
    public void publishVersionChange(String employeeId, String action) {
        try {
            String payload = employeeId + ":" + action;
            redisTemplate.convertAndSend(CHANNEL, payload);
            log.debug("已发布 Token 版本号变更通知: employeeId={}, action={}", employeeId, action);
        } catch (Exception e) {
            // 发布失败不影响主流程，Caffeine TTL 兜底
            log.warn("发布 Token 版本号变更通知失败 employeeId={}，各实例将在缓存过期后刷新", employeeId, e);
        }
    }

    /**
     * 收到其他实例（或本实例）发布的版本号变更通知时，根据操作类型处理本地缓存。
     * <p>
     * <ul>
     *   <li>{@code disable}：evict token version + markDisabled</li>
     *   <li>{@code logout}：仅 evict token version（用户仍为正常状态）</li>
     *   <li>{@code enable}：clearDisabled（清除禁用标记）</li>
     * </ul>
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody()).replace("\"", "");
            if (body.isBlank()) {
                return;
            }
            // 解析消息格式：employeeId:action
            String employeeId;
            String action;
            int separatorIdx = body.lastIndexOf(':');
            if (separatorIdx > 0) {
                employeeId = body.substring(0, separatorIdx);
                action = body.substring(separatorIdx + 1);
            } else {
                // 兼容旧格式（纯 employeeId），按 disable 处理
                employeeId = body;
                action = ACTION_DISABLE;
            }
            if (employeeId.isBlank()) {
                return;
            }

            switch (action) {
                case ACTION_DISABLE:
                    userStatusCache.evictTokenVersionLocal(employeeId);
                    userStatusCache.markDisabledLocal(employeeId);
                    log.debug("收到用户禁用通知，已刷新本地缓存: employeeId={}", employeeId);
                    break;
                case ACTION_LOGOUT:
                    userStatusCache.evictTokenVersionLocal(employeeId);
                    log.debug("收到强制下线通知，已清除本地 token version 缓存: employeeId={}", employeeId);
                    break;
                case ACTION_ENABLE:
                    userStatusCache.clearDisabledLocal(employeeId);
                    log.debug("收到用户启用通知，已清除本地禁用标记: employeeId={}", employeeId);
                    break;
                default:
                    // 未知操作类型，保守处理（按 disable）
                    userStatusCache.evictTokenVersionLocal(employeeId);
                    userStatusCache.markDisabledLocal(employeeId);
                    log.warn("收到未知操作类型的通知: employeeId={}, action={}", employeeId, action);
                    break;
            }
        } catch (Exception e) {
            log.warn("处理 Token 版本号变更通知失败", e);
        }
    }
}
