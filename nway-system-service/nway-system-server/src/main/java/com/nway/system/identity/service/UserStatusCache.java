package com.nway.system.identity.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.identity.domain.enums.UserStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 用户状态缓存。
 * <p>
 * 独立于 token 缓存，用于在 {@link LoginService#getLoginUser} 中二次校验用户状态，
 * 缩短禁用用户的生效窗口期（30 秒内全局生效）。
 * <p>
 * 两级缓存：
 * <ul>
 *   <li>L1 Caffeine（30 秒）：拦截大部分流量</li>
 *   <li>L2 Redis（5 分钟）：多实例共享，禁用时主动写入</li>
 * </ul>
 * 未命中时返回 null（视为 using，不阻断），仅在显式标记 disabled 时拦截。
 * Redis 不可用时降级到 L1，容忍短暂不一致（最长 30 秒）。
 *
 * @author nway
 */
@Slf4j
@Component
public class UserStatusCache {

    private static final String KEY_PREFIX = "nway_user_status:";
    private static final String TOKEN_VER_KEY_PREFIX = "nway_token_ver:";
    private static final long REDIS_TTL_SECONDS = 5 * 60L;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * L1 本地缓存。仅缓存 disabled 状态（using 不缓存，减少内存占用）。
     */
    private final Cache<String, Boolean> disabledCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    /**
     * L1 本地缓存。缓存 token 版本号，用于 JWT 撤销检查。
     */
    private final Cache<String, Integer> tokenVersionCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    /**
     * 判断用户是否已禁用。
     *
     * @param employeeId 工号
     * @return true 表示已禁用；false 或无法确认表示未禁用（不阻断）
     */
    public boolean isDisabled(String employeeId) {
        String key = KEY_PREFIX + employeeId;
        Boolean local = disabledCache.getIfPresent(key);
        if (Boolean.TRUE.equals(local)) {
            return true;
        }
        try {
            Object redisVal = redisTemplate.opsForValue().get(key);
            if (UserStatusEnum.DISABLED.getValue().equals(redisVal)) {
                disabledCache.put(key, Boolean.TRUE);
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，用户状态校验降级到本地缓存 employeeId={}", employeeId, e);
        }
        return false;
    }

    /**
     * 标记用户为禁用状态（禁用用户时调用）。
     */
    public void markDisabled(String employeeId) {
        String key = KEY_PREFIX + employeeId;
        disabledCache.put(key, Boolean.TRUE);
        try {
            redisTemplate.opsForValue().set(key, UserStatusEnum.DISABLED.getValue(), REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写用户禁用状态到 Redis 失败 employeeId={}，仅本地缓存生效", employeeId, e);
        }
    }

    /**
     * 清除用户禁用标记（启用用户时调用）。
     */
    public void clearDisabled(String employeeId) {
        String key = KEY_PREFIX + employeeId;
        disabledCache.invalidate(key);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("清除 Redis 用户禁用状态失败 employeeId={}", employeeId, e);
        }
    }

    // ==================== Token 版本号 ====================

    /**
     * 获取用户的 Token 版本号（用于 JWT 撤销检查）。
     * <p>
     * L1 Caffeine (30s) → L2 Redis (5min)。
     * 未命中时返回 -1（表示需要从 DB 加载）。
     *
     * @param employeeId 工号
     * @return token 版本号，未缓存时返回 -1
     */
    public int getTokenVersion(String employeeId) {
        String key = TOKEN_VER_KEY_PREFIX + employeeId;
        Integer local = tokenVersionCache.getIfPresent(key);
        if (local != null) {
            return local;
        }
        try {
            Object redisVal = redisTemplate.opsForValue().get(key);
            if (redisVal != null) {
                int version = Integer.parseInt(redisVal.toString());
                tokenVersionCache.put(key, version);
                return version;
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，token 版本号降级 employeeId={}", employeeId, e);
        }
        return -1;
    }

    /**
     * 设置用户的 Token 版本号（登录时/刷新时 从 DB 加载后写入缓存）。
     *
     * @param employeeId   工号
     * @param tokenVersion 版本号
     */
    public void setTokenVersion(String employeeId, int tokenVersion) {
        String key = TOKEN_VER_KEY_PREFIX + employeeId;
        tokenVersionCache.put(key, tokenVersion);
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(tokenVersion), REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写 token 版本号到 Redis 失败 employeeId={}", employeeId, e);
        }
    }

    /**
     * 清除用户 Token 版本号缓存（强制下线/改密码时调用，迫使下次请求从 DB 加载新版本号）。
     *
     * @param employeeId 工号
     */
    public void evictTokenVersion(String employeeId) {
        String key = TOKEN_VER_KEY_PREFIX + employeeId;
        tokenVersionCache.invalidate(key);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("清除 Redis token 版本号缓存失败 employeeId={}", employeeId, e);
        }
    }

    // ==================== Pub/Sub 本地缓存操作（仅操作 Caffeine，不触碰 Redis） ====================

    /**
     * 仅 invalidate 本地 Caffeine 中的 Token 版本号缓存。
     * <p>
     * 由 {@link TokenVersionPubSubService} 在收到 Redis Pub/Sub 通知时调用，
     * 不需要再操作 Redis（发布方已处理 Redis 缓存清除）。
     *
     * @param employeeId 工号
     */
    public void evictTokenVersionLocal(String employeeId) {
        String key = TOKEN_VER_KEY_PREFIX + employeeId;
        tokenVersionCache.invalidate(key);
    }

    /**
     * 仅在本地 Caffeine 中标记用户为禁用。
     * <p>
     * 由 {@link TokenVersionPubSubService} 在收到 Redis Pub/Sub 通知时调用，
     * 使本实例立即感知用户禁用状态，无需等待 Caffeine TTL 过期。
     *
     * @param employeeId 工号
     */
    public void markDisabledLocal(String employeeId) {
        String key = KEY_PREFIX + employeeId;
        disabledCache.put(key, Boolean.TRUE);
    }

    /**
     * 仅清除本地 Caffeine 中的禁用标记。
     * <p>
     * 由 {@link TokenVersionPubSubService} 在收到用户启用通知时调用，
     * 使本实例立即感知用户恢复正常，无需等待 Caffeine TTL 过期。
     *
     * @param employeeId 工号
     */
    public void clearDisabledLocal(String employeeId) {
        String key = KEY_PREFIX + employeeId;
        disabledCache.invalidate(key);
    }
}
