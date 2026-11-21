package com.nway.system.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.Set;

/**
 * 安全响应头配置。
 * <p>
 * 添加 CSP、X-Content-Type-Options、X-Frame-Options 等安全响应头。
 * 微前端 iframe 场景使用 X-Frame-Options: SAMEORIGIN；
 * <ul>
 *   <li>生产环境：移除 unsafe-eval，仅保留 unsafe-inline（Element Plus 样式需要）</li>
 *   <li>开发/测试环境：保留 unsafe-inline 和 unsafe-eval（Vue HMR 需要）</li>
 * </ul>
 * </p>
 *
 * @author nway
 */
@Configuration
public class SecurityHeaderConfig implements WebMvcConfigurer {

    /** 允许使用宽松 CSP 的开发/测试 profile */
    private static final Set<String> DEV_PROFILES = Set.of("local", "dev", "test");

    /** 生产环境 CSP：移除 unsafe-eval */
    private static final String CSP_PRODUCTION =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                    + "style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; "
                    + "connect-src 'self' https: wss:; font-src 'self' data:; "
                    + "frame-src 'self';";

    /** 开发环境 CSP：保留 unsafe-eval（Vue HMR / devtools 需要） */
    private static final String CSP_DEVELOPMENT =
            "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                    + "style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; "
                    + "connect-src 'self' https: ws: wss:; font-src 'self' data:; "
                    + "frame-src 'self';";

    @Autowired
    private Environment environment;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        boolean isDev = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(DEV_PROFILES::contains);
        String csp = isDev ? CSP_DEVELOPMENT : CSP_PRODUCTION;

        registry.addInterceptor(new SecurityHeaderInterceptor(csp))
                // 开放接口不需要这些安全头
                .excludePathPatterns("/open/**");
    }

    /**
     * 安全响应头拦截器，在 postHandle 阶段向响应中添加安全头。
     */
    private static class SecurityHeaderInterceptor implements HandlerInterceptor {

        private final String contentSecurityPolicy;

        SecurityHeaderInterceptor(String contentSecurityPolicy) {
            this.contentSecurityPolicy = contentSecurityPolicy;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response,
                               Object handler, org.springframework.web.servlet.ModelAndView modelAndView) {
            // 防止重复添加
            if (response.containsHeader("Content-Security-Policy")) {
                return;
            }
            response.setHeader("Content-Security-Policy", contentSecurityPolicy);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "SAMEORIGIN");
            response.setHeader("X-XSS-Protection", "1; mode=block");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        }
    }
}
