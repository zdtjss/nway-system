package com.nway.system.identity.web.interceptor;

import com.nway.system.identity.service.LoginService;
import com.nway.system.tenant.support.TenantStatusCache;
import com.nway.system.tenant.support.AdminGrantCacheManager;
import com.nway.system.tenant.web.model.AdminGrantDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 登录拦截器（JWT 无状态验签）。
 * <p>
 * 从 Authorization Header 获取 JWT，本地 HMAC-SHA256 验签，不查 Redis。
 * 验签通过后解析 LoginUser 放入 ThreadLocal，再进行接口权限校验。
 *
 * @author nway
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";
    private static final String TRACE_HEADER = "X-Trace-Id";

    /** 需先改密状态下仍允许访问的接口后缀 */
    private static final Set<String> PWD_RESET_ALLOWED_PATHS = Set.of(
            "/login/resetPassword", "/login/logout", "/login/getLoginUser",
            "/login/refresh", "/login/validateToken");

    @Autowired
    private LoginService loginService;
    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Autowired
    private Config config;

    @Autowired
    private AdminGrantCacheManager adminGrantCacheManager;

    @Autowired
    private TenantStatusCache tenantStatusCache;

    private final PathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 匿名路径配置。
     * <p>
     * 不使用 Lombok：该嵌套类曾被 IDE 增量编译写成空 Code（{@code ClassFormatError:
     * Invalid method Code length 0}），显式访问器可彻底避开该类问题。
     */
    @Component
    @Configuration
    @ConfigurationProperties(prefix = "url")
    public static class Config {
        private Set<String> anonymous;

        public Set<String> getAnonymous() {
            return anonymous;
        }

        public void setAnonymous(Set<String> anonymous) {
            this.anonymous = anonymous;
        }
    }

    @PostConstruct
    public void onPostConstruct() {
        config.anonymous = config.anonymous.stream()
                .map(url -> contextPath + url)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 生成 TraceId，放入 MDC 供日志输出
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        if (config.anonymous.stream().anyMatch(url -> pathMatcher.match(url, request.getRequestURI()))) {
            // 匿名路径可能复用上一请求的工作线程，先清除残留上下文，避免身份串号
            LoginUserContext.clean();
            return true;
        }

        boolean authenticated = false;
        try {
            // 仅允许通过 Header 传递 token，禁止 URL query parameter 传递
            String authorization = request.getHeader("Authorization");
            if (StringUtils.isBlank(authorization)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "未登录");
                return false;
            }
            // Bearer 前缀兼容
            String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;

            // JWT 本地验签，不查 Redis
            LoginUser loginUser = loginService.parseLoginUserFromJwt(token);
            if (loginUser == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "登录失效");
                return false;
            }

            // Token 版本号校验（强制下线/改密码后旧 Token 失效）
            if (!loginService.checkTokenVersion(loginUser.getEmployeeId(), loginUser.getTokenVersion())) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "登录已失效，请重新登录");
                return false;
            }

            LoginUserContext.set(loginUser);

            // 授权事实以库为准：超管/管理员标志不从 JWT 声明继承（缓存 60s，变更即驱逐）。
            // 顺序要求：必须先补齐 superAdmin 再判租户生命周期——否则刚被降权的用户
            // 仍会以“超管”身份绕过租户禁用/到期校验。
            AdminGrantDto adminGrant = adminGrantCacheManager.get(loginUser.getEmployeeId());
            if (adminGrant != null) {
                loginUser.setSuperAdmin(adminGrant.isSuperAdmin());
                loginUser.setAdmin(adminGrant.isAdmin());
                loginUser.setAdminTenantCodes(adminGrant.getTenantCodes());
            }

            // 租户生命周期：非超管的所属租户被禁用/到期后，拒绝其所有请求
            if (!loginUser.isSuperAdmin() && !tenantStatusCache.isUsable(loginUser.getTenantCode())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "租户已禁用或已到期");
                return false;
            }

            // 管理员工作租户：通过 X-Work-Tenant Header 指定当前操作的目标租户
            if (loginUser.isAdmin() && !loginUser.isSuperAdmin()) {
                String workTenant = request.getHeader("X-Work-Tenant");
                if (StringUtils.isNotBlank(workTenant)) {
                    if (loginUser.getAdminTenantCodes() == null || !loginUser.getAdminTenantCodes().contains(workTenant)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "无权操作该租户");
                        return false;
                    }
                    loginUser.setCurrentWorkTenantCode(workTenant);
                }
            }

            // 需先修改初始密码：仅放行改密/登出/续期等登录类接口
            if (loginUser.isPwdResetRequired() && !isPwdResetAllowed(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "请先修改初始密码后再操作");
                return false;
            }

            authenticated = true;
            return true;
        } finally {
            // 鉴权失败或异常退出时立即清理上下文，避免污染线程池中的后续请求
            if (!authenticated) {
                LoginUserContext.clean();
                MDC.remove(TRACE_ID);
            }
        }
    }

    /**
     * 需改密状态下允许访问的接口（改密、登出、当前用户信息、Token 续期/校验）。
     */
    private boolean isPwdResetAllowed(HttpServletRequest request) {
        String path = request.getServletPath();
        for (String suffix : PWD_RESET_ALLOWED_PATHS) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        LoginUserContext.clean();
        MDC.remove(TRACE_ID);
    }
}
