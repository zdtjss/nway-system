package com.nway.system.authorization.service.policy;

import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.authorization.domain.MergedPolicy;
import com.nway.system.common.tenant.TenantUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 统一权限策略多级缓存管理器。
 * <p>
 * 三级数据源：
 * <ol>
 *   <li>L1 本地缓存（Caffeine，1 分钟过期）：拦截 90%+ 流量</li>
 *   <li>L2 Redis 分布式缓存（10 分钟过期）：多实例共享，TTL 仅作失效通知失败时的兜底</li>
 *   <li>L3 数据库回源：通过 PolicyLoader 加载并合并</li>
 * </ol>
 * <p>
 * 缓存 value 为 Map&lt;String, MergedPolicy&gt;，key = appCode:resourceCode。
 * 失效失败的请求进入 {@link CacheEvictRetryQueue} 补偿。
 */
@Slf4j
@Component
public class PolicyCacheManager implements MessageListener {

    private static final String KEY_PREFIX = "POLICY:";
    /** L2 TTL：权限变更依赖失效通知收敛，TTL 只作为兜底，故取值较小（10 分钟） */
    private static final long REDIS_TTL_SECONDS = 10 * 60L;
    private static final String INVALIDATE_CHANNEL = "nway:policy_changed";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PolicyLoader policyLoader;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private CacheEvictRetryQueue cacheEvictRetryQueue;

    @Autowired
    private AuthzVersionService authzVersionService;

    /**
     * L1 本地缓存。
     * key = employeeId, value = Optional 包装的策略 Map（区分"已缓存空策略"与"未加载"）
     */
    private final LoadingCache<String, Optional<Map<String, MergedPolicy>>> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build(this::loadWithFallback);

    @PostConstruct
    public void subscribe() {
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(INVALIDATE_CHANNEL));
    }

    /**
     * 获取用户的完整策略 Map。
     *
     * @param employeeId 工号
     * @return appCode:resourceCode → MergedPolicy，返回 null 表示加载失败
     */
    public Map<String, MergedPolicy> get(String employeeId) {
        try {
            return localCache.get(employeeId).orElse(null);
        } catch (Exception e) {
            log.error("加载权限策略失败 employeeId={}", employeeId, e);
            return null;
        }
    }

    /**
     * 获取用户在指定资源上的策略。
     *
     * @param employeeId   工号
     * @param appCode      应用标识
     * @param resourceCode 资源标识
     * @return 合并后的策略，无权限返回 null
     */
    public MergedPolicy get(String employeeId, String appCode, String resourceCode) {
        Map<String, MergedPolicy> policyMap = get(employeeId);
        if (policyMap == null) {
            return null;
        }
        // 精确匹配
        MergedPolicy policy = policyMap.get(appCode + ":" + resourceCode);
        if (policy != null) {
            return policy;
        }
        // 通配匹配（appCode:*）
        return policyMap.get(appCode + ":*");
    }

    /**
     * 主动写入缓存（登录成功后调用）。
     */
    public void put(String employeeId, Map<String, MergedPolicy> policyMap) {
        Map<String, MergedPolicy> value = policyMap != null ? policyMap : Collections.emptyMap();
        localCache.put(employeeId, Optional.of(value));
        try {
            redisTemplate.opsForValue().set(key(employeeId), value, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写策略缓存到 Redis 失败 employeeId={}，仅本地缓存生效", employeeId, e);
        }
    }

    /**
     * 失效缓存（权限变更时调用）。
     * 同时广播到其他实例。
     */
    public void evict(String employeeId) {
        String key = key(employeeId);
        localCache.invalidate(employeeId);
        boolean redisDeleted = false;
        try {
            redisTemplate.delete(key);
            redisDeleted = true;
        } catch (Exception e) {
            log.warn("清理 Redis 策略缓存失败 employeeId={}，将进入补偿队列", employeeId, e);
        }
        boolean notified = false;
        try {
            redisTemplate.convertAndSend(INVALIDATE_CHANNEL, employeeId);
            notified = true;
        } catch (Exception e) {
            log.warn("发布策略缓存失效通知失败 employeeId={}，将进入补偿队列", employeeId, e);
        }
        if (!redisDeleted || !notified) {
            cacheEvictRetryQueue.enqueue(key, INVALIDATE_CHANNEL, employeeId);
        }
    }

    /**
     * 事务提交后失效缓存。
     * <p>
     * 角色/岗位绑定等变更在事务中完成，若在提交前失效，并发请求会把<b>旧数据</b>重新加载进缓存
     * （脏读窗口 1 分钟）。因此绑定类变更统一走本方法：有事务则注册 afterCommit，无事务则立即失效。
     */
    public void evictAfterCommit(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(employeeId);
                }
            });
        } else {
            evict(employeeId);
        }
    }

    /**
     * 收到其他实例的失效通知。
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String employeeId = new String(message.getBody()).replace("\"", "");
            if (!employeeId.isBlank()) {
                localCache.invalidate(employeeId);
            }
        } catch (Exception e) {
            log.warn("处理策略缓存失效通知失败", e);
        }
    }

    /**
     * Caffeine 加载回调：L2 Redis → L3 DB
     */
    private Optional<Map<String, MergedPolicy>> loadWithFallback(String employeeId) {
        // L2 Redis
        try {
            Object cached = redisTemplate.opsForValue().get(key(employeeId));
            if (cached instanceof Map<?, ?> map) {
                return Optional.of(toPolicyMap(map));
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，降级回源 DB 加载策略 employeeId={}", employeeId, e);
        }

        // L3 DB 回源
        // 缓存 key 为全局（employee_id 全局唯一），回源必须不受调用方租户上下文影响，
        // 否则会把"在他人租户视角下得到的空策略"写入全局缓存，导致误判无权限
        Map<String, MergedPolicy> policyMap = TenantUtil.callAsSystem(() -> policyLoader.loadFromDb(employeeId));
        if (policyMap == null) {
            policyMap = Collections.emptyMap();
        }

        // 回填 Redis
        try {
            redisTemplate.opsForValue().set(key(employeeId), policyMap, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("回填策略缓存到 Redis 失败 employeeId={}", employeeId, e);
        }

        return Optional.of(policyMap);
    }

    /**
     * 把 Redis 读回的原始 Map 还原为 Map&lt;String, MergedPolicy&gt;。
     * <p>
     * Redis value 序列化器为无类型信息的 JSON（JacksonJsonRedisSerializer），
     * Map 的 value 读回时是 LinkedHashMap，直接强转会在调用方取值时抛 ClassCastException，
     * 因此这里按值逐个还原（与 {@code AdminGrantDto.from} 同一处理惯例）。
     */
    private Map<String, MergedPolicy> toPolicyMap(Map<?, ?> raw) {
        Map<String, MergedPolicy> result = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key) || entry.getValue() == null) {
                continue;
            }
            Object value = entry.getValue();
            result.put(key, value instanceof MergedPolicy policy
                    ? policy
                    : JSON.parseObject(JSON.toJSONString(value), MergedPolicy.class));
        }
        return result;
    }

    private String key(String employeeId) {
        // key 携带全局授权版本：授权变更后版本递增，新请求自然落到新 key（未命中→回源），
        // 旧 key 在 TTL 内自然过期。这样即使失效通知丢失也不会长期读到旧权限。
        // 再携带工作租户：跨租户办公时权限视野收敛，避免不同租户之间共用同一份缓存。
        return KEY_PREFIX + authzVersionService.current() + ":" + employeeId + ":"
                + workTenantSuffix();
    }

    private String workTenantSuffix() {
        try {
            com.nway.system.common.security.LoginUser current =
                    com.nway.system.common.security.LoginUserContext.current();
            return current == null || current.getCurrentWorkTenantCode() == null
                    ? "" : current.getCurrentWorkTenantCode();
        } catch (RuntimeException e) {
            // 无登录上下文（系统任务）：按未指定工作租户处理
            return "";
        }
    }
}
