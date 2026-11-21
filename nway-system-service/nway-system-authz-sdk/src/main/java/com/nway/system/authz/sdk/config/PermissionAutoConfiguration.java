package com.nway.system.authz.sdk.config;

import com.nway.system.authz.sdk.client.CurrentUserResolver;
import com.nway.system.authz.sdk.client.PermissionPolicyClient;
import com.nway.system.authz.sdk.interceptor.PermissionAopInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 权限 SDK 自动配置。
 * <p>
 * 按需注册：
 * <ul>
 *   <li>{@link PermissionPolicyClient} — 必须配置 nway.permission.platform-url 与 app-code，
 *       推荐 app-secret（Token 自动刷新），兼容静态 app-token</li>
 *   <li>{@link PermissionAopInterceptor} — Spring AOP 在 classpath 时且有 CurrentUserResolver 实现</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PermissionProperties.class)
@ConditionalOnProperty(prefix = "nway.permission", name = "platform-url")
public class PermissionAutoConfiguration {

    @Bean
    public PermissionPolicyClient permissionPolicyClient(PermissionProperties properties) {
        PermissionPolicyClient client = new PermissionPolicyClient(
                properties.getPlatformUrl(), properties.getAppCode(),
                properties.getAppToken(), properties.getAppSecret());
        log.info("权限 SDK 客户端已初始化，平台地址: {}，应用: {}，Token 模式: {}",
                properties.getPlatformUrl(), properties.getAppCode(),
                properties.getAppSecret() != null ? "appSecret 自动刷新" : "静态 appToken（已过时）");
        return client;
    }

    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnBean(CurrentUserResolver.class)
    public PermissionAopInterceptor permissionAopInterceptor(PermissionPolicyClient client,
                                                             CurrentUserResolver userResolver) {
        return new PermissionAopInterceptor(client, userResolver);
    }
}
