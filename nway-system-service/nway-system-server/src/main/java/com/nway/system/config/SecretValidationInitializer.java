package com.nway.system.config;

import com.nway.system.config.properties.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 密钥启动校验。
 * <p>
 * 应用启动时校验 JWT 密钥配置：
 * <ul>
 *   <li>密钥为空：抛出异常阻止启动；</li>
 *   <li>密钥长度不足 32 字符：抛出异常阻止启动；</li>
 *   <li>密钥为默认值 + 非开发/测试环境：抛出异常阻止启动；</li>
 *   <li>密钥为默认值 + 开发/测试环境：打印 WARN 日志提醒。</li>
 * </ul>
 * </p>
 *
 * @author nway
 */
@Slf4j
@Component
public class SecretValidationInitializer implements ApplicationRunner {

    /** JWT 密钥默认值，生产环境必须替换 */
    private static final String DEFAULT_JWT_SECRET = "nway-system-jwt-secret-key-2026-please-change-in-production";

    /** 允许使用默认密钥的开发/测试 profile */
    private static final Set<String> DEV_PROFILES = Set.of("local", "dev", "test");

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String secret = jwtProperties.getSecret();

        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT 密钥（nway.jwt.secret）未配置，禁止启动：请在 application.yml 中配置非空密钥");
        }

        if (secret.length() < 32) {
            throw new IllegalStateException("JWT 密钥（nway.jwt.secret）长度不足 32 字符（当前 " + secret.length() + "），禁止启动：请使用足够强度的密钥");
        }

        if (DEFAULT_JWT_SECRET.equals(secret)) {
            boolean isDevProfile = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(DEV_PROFILES::contains);
            if (isDevProfile) {
                log.warn("【安全提醒】JWT 密钥（nway.jwt.secret）仍为默认值，请在非开发环境中修改为自定义密钥");
            } else {
                throw new IllegalStateException(
                        "JWT 密钥（nway.jwt.secret）仍为默认值，非开发/测试环境禁止使用默认密钥。"
                                + "请在环境变量 JWT_SECRET 或配置文件中设置自定义密钥");
            }
        }

        // 校验开放平台独立密钥（如果已配置）
        String openSecret = jwtProperties.getOpenSecret();
        if (openSecret != null && !openSecret.isBlank()) {
            if (openSecret.length() < 32) {
                throw new IllegalStateException(
                        "开放平台 JWT 密钥（nway.jwt.open-secret）长度不足 32 字符（当前 " + openSecret.length()
                                + "），禁止启动：请使用足够强度的密钥");
            }
            if (openSecret.equals(secret)) {
                throw new IllegalStateException(
                        "开放平台密钥（nway.jwt.open-secret）与用户 Token 密钥相同，禁止启动："
                                + "两套 Token 必须使用不同密钥以隔离风险");
            }
            log.info("开放平台使用独立签名密钥");
        } else {
            // 非开发/测试环境必须配置独立密钥，避免开放平台 Token 与用户 Token 共用密钥
            boolean isDevProfile = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(DEV_PROFILES::contains);
            if (isDevProfile) {
                log.warn("【安全提醒】开放平台未配置独立密钥（nway.jwt.open-secret），当前降级使用用户 Token 密钥，"
                        + "生产环境必须配置");
            } else {
                throw new IllegalStateException(
                        "开放平台 JWT 密钥（nway.jwt.open-secret）未配置，非开发/测试环境禁止启动："
                                + "请在环境变量 JWT_OPEN_SECRET 或配置文件中设置独立密钥");
            }
        }
    }
}
