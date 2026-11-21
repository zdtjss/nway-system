package com.nway.system.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性。
 *
 * @author nway
 */
@Data
@Component
@ConfigurationProperties(prefix = "nway.jwt")
public class JwtProperties {

    /** HMAC-SHA256 签名密钥（用户 Token） */
    private String secret = "nway-system-jwt-secret-key-2026-please-change-in-production";

    /** 开放平台独立签名密钥，为空时降级使用 secret */
    private String openSecret;

    /** Token 有效期（毫秒），默认 24 小时（空闲超过该时长需重新登录） */
    private long expireMillis = 24 * 60 * 60 * 1000L;

    /** 续期阈值（毫秒），剩余不足此时间可续期，默认 5 分钟 */
    private long refreshThresholdMillis = 5 * 60 * 1000L;

    /** 单个 Token 最大续期次数，默认 30 次（24h × 30 ≈ 30 天最长会话） */
    private int maxRefreshCount = 30;

    /**
     * 获取开放平台签名密钥。
     * 若未单独配置 openSecret，则降级使用 secret（向后兼容）。
     */
    public String getEffectiveOpenSecret() {
        return (openSecret != null && !openSecret.isBlank()) ? openSecret : secret;
    }
}
