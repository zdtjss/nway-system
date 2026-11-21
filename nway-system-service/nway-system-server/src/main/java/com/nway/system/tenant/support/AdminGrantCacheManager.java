package com.nway.system.tenant.support;

import com.nway.system.authorization.service.policy.CacheEvictRetryQueue;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.nway.system.tenant.web.model.AdminGrantDto;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.tenant.dao.AdminGrantDao;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 管理员授权多级缓存。
 * <p>
 * 两级数据源：
 * <ol>
 *   <li>L1 本地缓存（Caffeine，1 分钟过期）：拦截高频调用，单实例防击穿</li>
 *   <li>L2 Redis（10 分钟过期）：多实例共享；授权变更时通过 {@link #evict} 删除，其余实例 L1 miss 后回源</li>
 *   <li>L3 数据库回源（Redis miss 时）</li>
 * </ol>
 * <p>
 * 与 {@link PolicyCacheManager} 保持一致的降级语义：
 * Redis 不可用时自动降级到 L1 → DB，保证服务可用；
 * 失效失败的请求进入 {@link CacheEvictRetryQueue} 补偿，最坏收敛时间为 L2 TTL。
 * </p>
 */
@Slf4j
@Component
public class AdminGrantCacheManager implements MessageListener {

    /**
     * L2 缓存 key 前缀。
     * <p>
     * 带 {@code V2} 版本：{@code AdminGrantDto} 新增 {@code superAdmin} 字段后，
     * 旧缓存（无该字段，反序列化为 false）会让超管在 L2 TTL（最长 10min）内
     * 丢失超管能力。升级时换前缀使旧条目自然失效，避免上线窗口内的权限回退。
     */
    private static final String KEY_PREFIX = "SYS_ADMIN_GRANT_V2:";
    /** L2 TTL：撤权依赖失效通知收敛，TTL 只作为兜底，故取值较小（10 分钟） */
    private static final long REDIS_TTL_SECONDS = 10 * 60L;
    private static final String INVALIDATE_CHANNEL = "nway:admin_grant_changed";

    @Autowired
    private AdminGrantDao adminGrantDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;
    @Autowired
    private CacheEvictRetryQueue cacheEvictRetryQueue;

    private final LoadingCache<String, Optional<AdminGrantDto>> localCache = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build(this::loadWithFallback);

    @PostConstruct
    public void subscribe() {
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(INVALIDATE_CHANNEL));
    }

    /**
     * 获取管理员授权信息。
     *
     * @param employeeId 工号
     * @return 授权信息；加载失败时返回空授权（非管理员），保证调用方 fail-closed
     */
    public AdminGrantDto get(String employeeId) {
        try {
            return localCache.get(key(employeeId)).orElseGet(AdminGrantDto::new);
        } catch (Exception e) {
            log.error("加载管理员授权失败 employeeId={}", employeeId, e);
            return new AdminGrantDto();
        }
    }

    /**
     * 失效指定用户的管理员授权缓存（授权变更后调用，同步删除 Redis 使多实例收敛）。
     * <p>
     * Redis 删除或广播失败时登记到补偿队列重试（避免撤权长时间不生效）。
     */
    public void evict(String employeeId) {
        String key = key(employeeId);
        localCache.invalidate(key);
        boolean redisDeleted = false;
        try {
            redisTemplate.delete(key);
            redisDeleted = true;
        } catch (Exception e) {
            log.warn("清理 Redis 管理员授权缓存失败 employeeId={}，将进入补偿队列", employeeId, e);
        }
        boolean notified = false;
        // 广播失效通知，使其他实例立即失效本地 L1 缓存（否则最长延迟 1 分钟）
        try {
            redisTemplate.convertAndSend(INVALIDATE_CHANNEL, employeeId);
            notified = true;
        } catch (Exception e) {
            log.warn("发布管理员授权失效通知失败 employeeId={}，将进入补偿队列", employeeId, e);
        }
        if (!redisDeleted || !notified) {
            cacheEvictRetryQueue.enqueue(key, INVALIDATE_CHANNEL, employeeId);
        }
    }

    /**
     * 收到其他实例发布的管理员授权变更通知时，立即失效本地 L1 缓存。
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String employeeId = new String(message.getBody()).replace("\"", "");
            if (employeeId.isBlank()) {
                return;
            }
            localCache.invalidate(key(employeeId));
        } catch (Exception e) {
            log.warn("处理管理员授权失效通知失败", e);
        }
    }

    /**
     * Caffeine 加载回调：L2 Redis → L3 DB，含降级。
     */
    private Optional<AdminGrantDto> loadWithFallback(String key) {
        String employeeId = key.substring(KEY_PREFIX.length());

        // L2 Redis
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                AdminGrantDto dto = AdminGrantDto.from(cached);
                if (dto != null) {
                    return Optional.of(dto);
                }
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，降级回源 DB 加载管理员授权 employeeId={}", employeeId, e);
        }

        // L3 DB 回源
        AdminGrantDto dto = loadFromDb(employeeId);
        // 回填 Redis（失败不影响返回）
        try {
            redisTemplate.opsForValue().set(key, dto, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("回填 Redis 管理员授权缓存失败 employeeId={}，仅本地缓存生效", employeeId, e);
        }
        return Optional.of(dto);
    }

    private AdminGrantDto loadFromDb(String employeeId) {
        // 缓存 key 为全局（employee_id 全局唯一），回源必须不受调用方租户上下文影响，
        // 否则会把"在他人租户视角下得到的空授权"写入全局缓存
        return TenantUtil.callAsSystem(() -> {
            AdminGrantDto dto = new AdminGrantDto();
            dto.setEmployeeId(employeeId);
            User user = userDao.getByEmployeeId(employeeId);
            dto.setRealName(user != null ? user.getRealName() : null);
            // 超管资格只能在库侧变更（无 API 入口），以库为准，不从 JWT 声明继承
            dto.setSuperAdmin(user != null && Boolean.TRUE.equals(user.getIsSuperAdmin()));
            boolean admin = user != null && Boolean.TRUE.equals(user.getIsAdmin());
            dto.setAdmin(admin);
            if (admin) {
                dto.setTenantCodes(adminGrantDao.queryTenantCodes(employeeId));
            } else {
                dto.setTenantCodes(Collections.emptyList());
            }
            return dto;
        });
    }

    private String key(String employeeId) {
        // sys_user.employee_id 全局唯一，无需租户前缀
        return KEY_PREFIX + employeeId;
    }
}