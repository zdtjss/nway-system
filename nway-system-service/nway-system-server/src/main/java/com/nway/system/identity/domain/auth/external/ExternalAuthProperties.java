package com.nway.system.identity.domain.auth.external;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部身份认证登录配置。
 * <p>
 * 平台本身不实现具体身份源：外部系统（OA 迁移期为 eccp-portal2）完成身份校验后返回用户档案，
 * nway-system 只负责落地影子账号并签发平台 JWT。属于平台通用登录扩展点，默认关闭。
 *
 * @author nway
 */
@Data
@Component
@ConfigurationProperties(prefix = "nway.login.external")
public class ExternalAuthProperties {

    /** 是否启用外部身份认证登录 */
    private boolean enabled = false;

    /** 外部认证服务地址（含 context-path）*/
    private String baseUrl;

    /** 外部认证路径 */
    private String loginPath;

    /** 外部自助改密路径（需旧密码校验） */
    private String changePasswordPath;

    /** 内部调用凭据（请求头 X-Nway-Token），由外部系统配置的同一密钥校验 */
    private String token;

    /** 外部用户落地到 nway 时的归属租户 */
    private String tenantCode;

    /** 外部认证通过但本地无账号时，是否自动补建影子账号 */
    private boolean autoProvision = true;

    /** 外部认证判定账号不存在时，是否回退到平台本地密码认证 */
    private boolean localFallback = true;

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 读取超时（毫秒） */
    private int readTimeoutMillis = 3000;

    /**
     * 配置是否足以发起一次外部认证调用。
     */
    public boolean isConfigured() {
        return enabled
                && StringUtils.isNotBlank(baseUrl)
                && StringUtils.isNotBlank(token);
    }
}
