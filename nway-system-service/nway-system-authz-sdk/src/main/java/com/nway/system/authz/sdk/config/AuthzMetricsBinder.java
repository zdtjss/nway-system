package com.nway.system.authz.sdk.config;

import com.nway.system.authz.sdk.client.AuthzBundleClient;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 授权 SDK 指标：决策次数、拒绝次数、拉取错误、缓存策略包数量。
 * <p>
 * 仅在使用方引入 Micrometer（Spring Boot Actuator 默认带）时注册。
 */
public class AuthzMetricsBinder {

    public AuthzMetricsBinder(AuthzBundleClient client, MeterRegistry registry) {
        Gauge.builder("nway.authz.decisions", client, c -> c.stats().decisions())
                .description("接口级授权决策次数")
                .register(registry);
        Gauge.builder("nway.authz.denies", client, c -> c.stats().denies())
                .description("授权拒绝次数")
                .register(registry);
        Gauge.builder("nway.authz.errors", client, c -> c.stats().errors())
                .description("策略包拉取错误次数")
                .register(registry);
        Gauge.builder("nway.authz.cached.bundles", client, c -> c.stats().cachedBundles())
                .description("本地缓存的策略包数量")
                .register(registry);
    }
}
