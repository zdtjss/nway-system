package com.nway.system.config;

import com.nway.system.common.security.interceptor.InternalServiceInterceptor;
import com.nway.system.identity.web.interceptor.LoginInterceptor;
import com.nway.system.openplatform.web.interceptor.OpenApiInterceptor;
import com.nway.system.authorization.web.interceptor.PermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
public class SpringMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private OpenApiInterceptor openApiInterceptor;

    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @Autowired
    private InternalServiceInterceptor internalServiceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 登录认证拦截器（JWT 验签 + 用户上下文）
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns("/open/**", "/internal/**", "/actuator/**", "/error",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**");

        // 2. 权限策略拦截器（服务端 URL → appCode + resourceCode + action 判定，未登记资源一律拒绝）
        //    排除开放接口（有独立鉴权）、内部服务接口、公共接口、登录接口、错误转发
        //    /file/ticketDownload 为票据直链：匿名进入并由 Controller 校验票据，不经过登录态权限判定
        registry.addInterceptor(permissionInterceptor)
                .excludePathPatterns("/open/**", "/internal/**", "/actuator/**", "/login/**", "/myMenu/**", "/error",
                        "/file/ticketDownload",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**");

        // 3. 开放平台拦截器（AppKey+Secret 鉴权）
        registry.addInterceptor(openApiInterceptor)
                .addPathPatterns("/open/**")
                .excludePathPatterns("/open/token");

        // 4. 内部服务拦截器（平台内服务令牌：iss/aud/scope/租户 校验）
        registry.addInterceptor(internalServiceInterceptor)
                .addPathPatterns("/internal/**");
    }
}
