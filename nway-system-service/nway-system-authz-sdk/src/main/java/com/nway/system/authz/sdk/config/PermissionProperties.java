package com.nway.system.authz.sdk.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 权限 SDK 配置属性。
 * <p>
 * 配置示例（推荐：appCode + appSecret，SDK 自动获取并刷新 Token）：
 * <pre>
 * nway:
 *   permission:
 *     platform-url: http://localhost:8081/nway-system
 *     app-code: order-system
 *     app-secret: xxx
 * </pre>
 * 兼容旧方式：仅配置 app-token 时使用静态 Token（2 小时过期后需自行更换，不推荐）。
 */
@Data
@ConfigurationProperties(prefix = "nway.permission")
public class PermissionProperties {

    /**
     * 平台地址（如 http://localhost:8081/nway-system）
     */
    @NotBlank(message = "平台地址不能为空 (nway.permission.platform-url)")
    private String platformUrl;

    /**
     * 本应用标识（对应 sys_open_app.app_code）
     */
    @NotBlank(message = "应用标识不能为空 (nway.permission.app-code)")
    private String appCode;

    /**
     * 应用密钥（推荐配置，SDK 自动通过 POST /open/token 获取并刷新应用 Token）
     */
    private String appSecret;

    /**
     * 开放平台应用 Token（静态方式，已过时；配置了 app-secret 时忽略此值）
     */
    private String appToken;
}
