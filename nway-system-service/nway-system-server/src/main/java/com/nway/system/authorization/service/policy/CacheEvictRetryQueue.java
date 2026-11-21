package com.nway.system.authorization.service.policy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存失效补偿队列。
 * <p>
 * Redis 不可用时，缓存删除与失效广播都会失败，若不补偿会导致"撤权/改权限后长时间不生效"。
 * 此处记录待补偿的失效请求并定时重试；配合 L2 TTL 收敛，最坏收敛时间为 TTL + 一个重试周期。
 *
 * @author nway
 */
@Slf4j
@Component
public class CacheEvictRetryQueue {

    /** 队列上限，防止 Redis 长时间不可用导致内存膨胀 */
    private static final int MAX_PENDING = 10_000;

    private final Map<String, PendingEvict> pending = new ConcurrentHashMap<>();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 登记一次失败的缓存失效请求（相同 key 去重）。
     *
     * @param redisKey 待删除的 Redis key
     * @param channel  失效广播频道
     * @param payload  广播内容（通常为 employeeId）
     */
    public void enqueue(String redisKey, String channel, String payload) {
        if (pending.size() >= MAX_PENDING && !pending.containsKey(redisKey)) {
            log.error("缓存失效补偿队列已满({})，丢弃补偿请求 key={}", MAX_PENDING, redisKey);
            return;
        }
        pending.put(redisKey, new PendingEvict(redisKey, channel, payload));
    }

    /**
     * 定时重试未完成的失效请求（每 30 秒一次）。
     */
    @Scheduled(fixedDelay = 30_000L)
    public void retryPending() {
        if (pending.isEmpty()) {
            return;
        }
        pending.forEach((key, task) -> {
            try {
                redisTemplate.delete(task.redisKey());
                if (task.channel() != null) {
                    redisTemplate.convertAndSend(task.channel(), task.payload());
                }
                pending.remove(key);
                log.info("缓存失效补偿成功 key={}", key);
            } catch (Exception e) {
                log.warn("缓存失效补偿失败，将在下个周期重试 key={}", key);
            }
        });
    }

    /**
     * 待补偿的失效请求。
     */
    private record PendingEvict(String redisKey, String channel, String payload) {
    }
}