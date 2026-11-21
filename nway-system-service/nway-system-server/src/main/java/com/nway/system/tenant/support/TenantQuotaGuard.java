package com.nway.system.tenant.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 租户级请求配额（QPS）守卫。
 * <p>
 * 用于防止单一租户的异常流量拖垮全局（"吵闹邻居"）：
 * <ul>
 *   <li>进程内固定窗口计数，不引入 Redis 往返，避免限流本身成为性能瓶颈</li>
 *   <li>内部异常或 Redis/依赖不可用时<b>放行</b>（fail-open），仅作为配额保护而非安全边界
 *       —— 权限与租户隔离由 TenantPermissionStrategy / PermissionInterceptor 保证</li>
 *   <li>多实例部署时集群总配额 ≈ 实例数 × 配置值，如需精确全局配额应改用集中式限流</li>
 * </ul>
 * 配置项 {@code nway.tenant.quota.internal-qps}：≤0 表示不限制（默认 1000）。
 *
 * @author nway
 */
@Slf4j
@Component
public class TenantQuotaGuard {

    /** 一分钟窗口的秒数 */
    private static final long WINDOW_MILLIS = 1000L;

    private final int qpsLimit;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public TenantQuotaGuard(@Value("${nway.tenant.quota.internal-qps:1000}") int qpsLimit) {
        this.qpsLimit = qpsLimit;
        if (qpsLimit <= 0) {
            log.info("租户级 QPS 配额未启用（nway.tenant.quota.internal-qps<=0）");
        }
    }

    /**
     * 尝试为指定租户获取一次请求许可。
     *
     * @param tenantCode 租户编码；为空视为不限制
     * @return true 允许；false 超出配额
     */
    public boolean tryAcquire(String tenantCode) {
        if (qpsLimit <= 0 || tenantCode == null || tenantCode.isBlank()) {
            return true;
        }
        try {
            long nowSecond = System.currentTimeMillis() / WINDOW_MILLIS;
            Window window = windows.computeIfAbsent(tenantCode, key -> new Window(nowSecond));
            return window.tryAcquire(nowSecond, qpsLimit);
        } catch (Exception e) {
            log.warn("租户配额检查异常，放行请求 tenantCode={}", tenantCode, e);
            return true;
        }
    }

    /**
     * 单租户的固定窗口计数器。
     */
    private static final class Window {

        private final AtomicLong currentSecond;
        private final AtomicInteger counter = new AtomicInteger();

        Window(long second) {
            this.currentSecond = new AtomicLong(second);
        }

        boolean tryAcquire(long second, int limit) {
            // 窗口切换时重置计数（CAS 保证只有一个线程执行重置）
            if (currentSecond.get() != second && currentSecond.compareAndSet(currentSecond.get(), second)) {
                counter.set(0);
            }
            return counter.incrementAndGet() <= limit;
        }
    }
}