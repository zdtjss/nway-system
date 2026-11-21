package com.nway.system.portal.service;

import com.nway.system.common.exception.NwaySystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 低代码菜单同步的跨实例分布式锁。
 * <p>
 * 低代码发布为低频操作，用 Redis（SET NX PX + Lua 比对持有者后删除）对
 * <b>同一目标应用</b>的 {@code sync / remove / disable} 串行化，解决原先仅靠
 * 实例内 {@code synchronized} 导致的多实例并发重复插入问题。
 * <p>
 * 锁粒度取 {@code appCode}：幂等键 {@code (tenant_code, app_code, external_id)} 中
 * appCode 是并发冲突维度，同应用串行即可，不同应用可并行。
 * <p>
 * 与 {@code sys_menu} 的活跃唯一索引（{@code uidx_menu_active_external}）形成双重兜底：
 * 即便锁因 Redis 故障失效，数据库唯一约束仍能阻止重复行。
 * <p>
 * 站内 {@code RedisTemplate} 的 value 序列化器是 JSON，会把 VALUE 写成带引号的串，
 * 影响 Lua 比对，故这里使用纯文本的 {@link StringRedisTemplate}（同 {@code RateLimiter}）。
 */
@Slf4j
@Component
public class LowcodeMenuSyncLock {

    private static final String KEY_PREFIX = "lowcode:menu:lock:";

    /** 释放锁：仅当 VALUE 与持有者 token 一致才删除，避免误删他人的锁。 */
    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    /** 默认等待获取锁时长。 */
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);
    /** 默认锁持有时长（覆盖单次同步事务；异常路径由超时自动释放）。 */
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> releaseScript;

    public LowcodeMenuSyncLock() {
        releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptText(RELEASE_SCRIPT);
        releaseScript.setResultType(Long.class);
    }

    /**
     * 按目标应用加锁（默认等待 10s、持有 60s）。
     *
     * @param appCode 目标应用编码（空则退化为全局锁键）
     */
    public Handle lock(String appCode) {
        return lock(appCode, DEFAULT_WAIT, DEFAULT_TTL);
    }

    /**
     * 按目标应用加锁。
     *
     * @throws NwaySystemException 等待超时或 Redis 不可用（fail-close：不获取锁不执行写操作）
     */
    public Handle lock(String appCode, Duration wait, Duration ttl) {
        String key = KEY_PREFIX + StringUtils.defaultIfBlank(appCode, "_");
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + wait.toMillis();
        do {
            try {
                Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
                if (Boolean.TRUE.equals(acquired)) {
                    return new Handle(key, token);
                }
            } catch (Exception e) {
                log.error("获取菜单同步分布式锁异常, key={}", key, e);
                throw new NwaySystemException("菜单同步锁不可用，请稍后重试");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NwaySystemException("等待菜单同步锁被中断");
            }
        } while (System.currentTimeMillis() < deadline);
        throw new NwaySystemException("菜单同步正在处理中，请稍后重试");
    }

    /** 锁句柄：try-with-resources 关闭即释放。 */
    public final class Handle implements AutoCloseable {

        private final String key;
        private final String token;

        private Handle(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public void close() {
            try {
                stringRedisTemplate.execute(releaseScript, Collections.singletonList(key), token);
            } catch (Exception e) {
                log.warn("释放菜单同步分布式锁失败, key={}", key, e);
            }
        }
    }
}