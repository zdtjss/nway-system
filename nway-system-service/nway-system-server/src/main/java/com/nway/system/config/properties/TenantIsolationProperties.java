package com.nway.system.config.properties;

import com.nway.system.common.persistence.TenantCodeStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 租户隔离开关。
 * <p>
 * {@code nway.tenant.allow-default-fallback}（默认 false）：开启后，写入缺少租户上下文时
 * 才允许回落到默认租户；仅用于初始化/迁移等特殊场景，正常运行必须保持关闭（fail-closed）。
 */
@Component
public class TenantIsolationProperties {

    @Value("${nway.tenant.allow-default-fallback:false}")
    public void setAllowDefaultFallback(boolean value) {
        TenantCodeStrategy.setAllowDefaultFallback(value);
    }
}