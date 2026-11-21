package com.nway.system.common.security.interceptor;

import com.nway.system.config.properties.InternalServiceProperties;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.JwtUtil;
import com.nway.system.common.security.LoginUserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 内部服务认证拦截器（/internal/**）。
 * <p>
 * 校验平台内服务令牌：签名 → 签发方 → 受众 → scope → 租户，
 * 通过后按令牌租户建立系统上下文。不放宽用户权限模型，仅服务间调用使用。
 * 密钥未配置时一律拒绝（fail-closed）。
 */
@Slf4j
@Component
public class InternalServiceInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final InternalServiceProperties properties;

    public InternalServiceInterceptor(InternalServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (StringUtils.isBlank(properties.getSecret())) {
            log.warn("内部服务接口未配置密钥，拒绝访问: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "内部服务认证未配置");
            return false;
        }

        String authorization = request.getHeader("Authorization");
        if (StringUtils.isBlank(authorization)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "缺少内部服务令牌");
            return false;
        }
        String token = authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()) : authorization;
        Claims claims = JwtUtil.parseToken(token, properties.getSecret());
        if (claims == null || JwtUtil.isOpenToken(claims)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "内部服务令牌无效或已过期");
            return false;
        }
        if (!issuerAllowed(claims)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "内部服务令牌签发方不被信任");
            return false;
        }
        if (!audienceMatches(claims)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "内部服务令牌受众不匹配");
            return false;
        }
        if (!scopeMatches(claims)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "内部服务令牌缺少 scope: " + properties.getRequiredScope());
            return false;
        }
        String tenantCode = JwtUtil.getTenantCode(claims);
        if (StringUtils.isBlank(tenantCode)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "内部服务令牌缺少租户");
            return false;
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setEmployeeId(StringUtils.defaultIfBlank(claims.getSubject(), "workflow"));
        loginUser.setRealName(StringUtils.defaultIfBlank(claims.get("name", String.class), "工作流引擎"));
        loginUser.setTenantCode(tenantCode);
        loginUser.setSuperAdmin(false);
        loginUser.setAdmin(false);
        LoginUserContext.set(loginUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        LoginUserContext.clean();
    }

    private boolean issuerAllowed(Claims claims) {
        List<String> allowed = properties.getAllowedIssuers();
        return allowed == null || allowed.isEmpty() || allowed.contains(claims.getIssuer());
    }

    private boolean audienceMatches(Claims claims) {
        String expected = properties.getAudience();
        if (StringUtils.isBlank(expected)) {
            return true;
        }
        Set<String> audience = claims.getAudience();
        return audience != null && audience.contains(expected);
    }

    private boolean scopeMatches(Claims claims) {
        String scope = claims.get("scope", String.class);
        if (StringUtils.isBlank(scope)) {
            return false;
        }
        String required = properties.getRequiredScope();
        return Arrays.stream(scope.split("\\s+"))
                .anyMatch(item -> "*".equals(item) || item.equals(required));
    }
}
