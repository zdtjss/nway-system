package com.nway.system.authorization.service.grant;

import com.nway.system.authorization.dao.AuthzVersionDao;
import com.nway.system.authorization.entity.SysAuthzVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 全局授权版本。
 * <p>
 * 授权变更只递增版本；缓存侧发现版本变化即整体失效，不再"查角色下所有用户逐个 evict"。
 * 版本读取有 2 秒本地缓存：变更后最迟 2 秒全实例可见（满足方案 ≤5s 的目标）。
 */
@Service
public class AuthzVersionService {

    private static final Logger log = LoggerFactory.getLogger(AuthzVersionService.class);

    /** 版本本地缓存时长（毫秒） */
    private static final long CACHE_MILLIS = 2_000L;

    private final AuthzVersionDao versionDao;

    private volatile long cachedVersion = -1;
    private volatile long cachedAt = 0;

    public AuthzVersionService(AuthzVersionDao versionDao) {
        this.versionDao = versionDao;
    }

    /** 当前全局授权版本；库不可用时返回 -1（调用方按"未知版本"处理，缓存仍可用）。 */
    public long current() {
        long now = System.currentTimeMillis();
        long cached = cachedVersion;
        if (cached >= 0 && now - cachedAt < CACHE_MILLIS) {
            return cached;
        }
        try {
            SysAuthzVersion version = versionDao.getGlobal();
            cachedVersion = version != null && version.getVersion() != null ? version.getVersion() : 1L;
            cachedAt = now;
            return cachedVersion;
        } catch (RuntimeException e) {
            log.warn("读取授权版本失败，沿用上次版本 {}：{}", cached, e.getMessage());
            return cached;
        }
    }

    /**
     * 递增全局版本（与授权写入同事务调用）。
     * <p>
     * 正确性由 {@link AuthzVersionDao#incrementAndGet()} 的单条原子 UPDATE 保证；
     * 这里的 synchronized 只用于减少同 JVM 内的争抢，不承担跨实例并发安全。
     */
    public synchronized long bump() {
        long next = versionDao.incrementAndGet();
        cachedVersion = next;
        cachedAt = System.currentTimeMillis();
        return next;
    }
}
