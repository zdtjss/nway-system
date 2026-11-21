package com.nway.system.authz.sdk.config;

import com.nway.system.authz.sdk.client.AuthzBundleClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * 授权 SDK 健康检查：输出决策次数、拒绝次数、最近一次拉取错误与缓存规模。
 * <p>
 * 近期（5 分钟）有平台拉取错误时标记为 {@code DEGRADED}，但不返回 DOWN —
 * SDK 采用 stale-while-error，引擎仍可用。
 */
public class AuthzHealthIndicator implements HealthIndicator {

    private static final long RECENT_ERROR_MILLIS = 5 * 60 * 1000L;

    private final AuthzBundleClient client;

    public AuthzHealthIndicator(AuthzBundleClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        AuthzBundleClient.Stats stats = client.stats();
        boolean recentError = stats.lastErrorAt() > 0
                && System.currentTimeMillis() - stats.lastErrorAt() < RECENT_ERROR_MILLIS;
        Health.Builder builder = recentError ? Health.status("DEGRADED") : Health.up();
        return builder
                .withDetail("decisions", stats.decisions())
                .withDetail("denies", stats.denies())
                .withDetail("errors", stats.errors())
                .withDetail("lastErrorAt", stats.lastErrorAt())
                .withDetail("cachedBundles", stats.cachedBundles())
                .build();
    }
}
