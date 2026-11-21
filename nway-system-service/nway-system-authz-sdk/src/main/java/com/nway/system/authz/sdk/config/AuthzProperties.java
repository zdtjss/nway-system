package com.nway.system.authz.sdk.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 授权 SDK 配置（{@code nway.authz.*}）。
 * <p>
 * 最小配置：
 * <pre>
 * nway:
 *   authz:
 *     platform-url: http://host:8081/nway-system
 *     app-code: order-system
 *     app-secret: ******
 * </pre>
 * {@code fail-open} 默认 false：平台不可用且无历史策略时拒绝（安全默认）。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "nway.authz")
public class AuthzProperties {

    /** 平台地址（含上下文路径） */
    @NotBlank(message = "nway.authz.platform-url 不能为空")
    private String platformUrl;

    /** 应用编码（对应 sys_open_app.app_code） */
    @NotBlank(message = "nway.authz.app-code 不能为空")
    private String appCode;

    /** 应用密钥（推荐，SDK 自动换取并刷新 Token） */
    private String appSecret;

    /** 静态应用 Token（已过时；配置 app-secret 时忽略） */
    private String appToken;

    /** 策略包有效缓存时长 */
    private Duration cacheTtl = Duration.ofMinutes(1);

    /** 平台不可用时沿用历史策略的最长时间（stale-while-error） */
    private Duration staleTtl = Duration.ofHours(12);

    /** 应急放行：仅重大故障人工开启；开启时打 ERROR 日志 */
    private boolean failOpen = false;

    /** 启动自检：拉取一次本应用资源目录/策略包，失败仅告警（P1：平台不可用不阻断启动） */
    private boolean startupCheck = true;

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration requestTimeout = Duration.ofSeconds(5);

    /**
     * MyBatis 插件绑定：mappedStatement id → 资源标识（如 {@code com.x.OrderMapper.selectPage: order}）。
     * 为空时不注册 MyBatis 插件。
     */
    private Map<String, String> mapperResources = new LinkedHashMap<>();
}
