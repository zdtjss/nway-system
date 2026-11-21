package com.nway.system.authz.sdk.config;

import com.nway.system.authz.sdk.client.AuthzBundleClient;
import com.nway.system.authz.sdk.client.SubjectResolver;
import com.nway.system.authz.sdk.client.ThreadLocalSubjectResolver;
import com.nway.system.authz.sdk.interceptor.AuthzInterceptor;
import com.nway.system.authz.sdk.interceptor.MyBatisAuthzInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 授权 SDK 自动配置（{@code nway.authz.*}）。
 * <p>
 * 子应用引入依赖并配置 {@code platform-url/app-code/app-secret} 即启用；
 * 接口级拦截器只对标注 {@code @RequiresPermission} 的端点生效。
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthzProperties.class)
@ConditionalOnProperty(prefix = "nway.authz", name = "platform-url")
public class AuthzAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthzAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AuthzBundleClient authzBundleClient(AuthzProperties properties) {
        log.info("nway 授权 SDK 已初始化：platform={}, appCode={}, tokenMode={}",
                properties.getPlatformUrl(), properties.getAppCode(),
                properties.getAppSecret() == null || properties.getAppSecret().isBlank()
                        ? "静态 appToken" : "appSecret 自动刷新");
        return new AuthzBundleClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SubjectResolver subjectResolver() {
        return new ThreadLocalSubjectResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    public AuthzInterceptor authzInterceptor(AuthzBundleClient client, SubjectResolver resolver) {
        return new AuthzInterceptor(client, resolver);
    }

    @Bean
    @ConditionalOnMissingBean(name = "nwayAuthzWebMvcConfigurer")
    @ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
    public WebMvcConfigurer nwayAuthzWebMvcConfigurer(AuthzInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor((HandlerInterceptor) interceptor).addPathPatterns("/**");
            }
        };
    }

    /**
     * 启动自检（只告警不阻断，P1）。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthzStartupChecker authzStartupChecker(AuthzBundleClient client, AuthzProperties properties) {
        return new AuthzStartupChecker(client, properties.isStartupCheck());
    }

    /**
     * Micrometer 指标（可选）。
     */
    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnMissingBean
    public AuthzMetricsBinder authzMetricsBinder(AuthzBundleClient client,
                                                 io.micrometer.core.instrument.MeterRegistry registry) {
        return new AuthzMetricsBinder(client, registry);
    }

    /**
     * MyBatis 数据权限插件（仅当配置了 mapper-resources 且 classpath 有 MyBatis）。
     */
    @Bean
    @ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
    @ConditionalOnMissingBean
    public MyBatisAuthzInterceptor myBatisAuthzInterceptor(AuthzBundleClient client,
                                                           SubjectResolver resolver,
                                                           AuthzProperties properties) {
        return new MyBatisAuthzInterceptor(client, resolver, properties.getMapperResources());
    }

    /**
     * Actuator 健康检查（可选）。
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnMissingBean(name = "nwayAuthzHealthIndicator")
    public AuthzHealthIndicator nwayAuthzHealthIndicator(AuthzBundleClient client) {
        return new AuthzHealthIndicator(client);
    }
}
