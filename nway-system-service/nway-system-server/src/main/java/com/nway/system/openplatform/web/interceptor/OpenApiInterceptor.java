package com.nway.system.openplatform.web.interceptor;

import com.nway.system.common.web.interceptor.RateLimiter;
import com.alibaba.fastjson2.JSON;
import com.nway.system.openplatform.service.OpenApiLogService;
import com.nway.system.openplatform.service.OpenAppService;
import com.nway.system.openplatform.service.OpenTokenService;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.util.ClientIpResolver;
import com.nway.system.common.security.JwtUtil;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.openplatform.web.context.OpenAppContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.openplatform.entity.SysOpenApp;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 开放平台 API 拦截器。
 * <p>
 * 负责：Token 验证 → scope 推断与校验 → IP 白名单 → 限流 → 设置上下文 → 审计日志。
 *
 * @author nway
 */
@Slf4j
@Component
public class OpenApiInterceptor implements HandlerInterceptor {

    @Autowired
    private OpenTokenService openTokenService;
    @Autowired
    private OpenAppService openAppService;
    @Autowired
    private OpenApiLogService openApiLogService;
    @Autowired
    private RateLimiter rateLimiter;
    @Autowired
    private ClientIpResolver clientIpResolver;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    private static final String START_TIME_ATTR = "openApiStartTime";

    /** 应用自身身份（client_credentials 模式）在租户隔离上下文中的占位工号 */
    private static final String OPEN_SYSTEM_EMPLOYEE_ID = "open-system";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 清理可能残留的上下文，避免线程复用导致身份/租户串号
        LoginUserContext.clean();
        OpenAppContext.clear();
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        String requestUri = request.getRequestURI();
        String apiPath = requestUri.startsWith(contextPath) ? requestUri.substring(contextPath.length()) : requestUri;

        // 1. 跳过 /open/token（认证端点）
        if ("/open/token".equals(apiPath)) {
            return true;
        }

        // 2. 验证应用 Token
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            sendError(response, 401, "缺少 Authorization Bearer Token");
            return false;
        }
        String token = authorization.substring(7);
        Claims claims = openTokenService.verifyToken(token);
        if (claims == null) {
            sendError(response, 401, "Token 无效或已过期");
            return false;
        }

        String appCode = JwtUtil.getAppCode(claims);
        String tenantCode = JwtUtil.getTenantCode(claims);
        List<String> grantedScopes = JwtUtil.getGrantedScopes(claims);
        String dataScopeType = JwtUtil.getDataScopeType(claims);
        List<String> dataScopeDeptIds = JwtUtil.getDataScopeDeptIds(claims);
        String employeeId = JwtUtil.getDelegateEmployeeId(claims);

        // 2.1 租户作用域必须存在：缺失时拒绝（避免退化为跨租户访问）
        if (StringUtils.isBlank(tenantCode)) {
            sendError(response, 403, "应用未归属租户，禁止访问开放接口");
            return false;
        }

        // 3. 从路径 + HTTP 方法推断 scope
        String requiredScope = resolveScope(apiPath, request.getMethod());
        if (requiredScope != null && !grantedScopes.contains(requiredScope) && !grantedScopes.contains("*")) {
            sendError(response, 403, "缺少 scope: " + requiredScope);
            return false;
        }

        // 4. IP 白名单校验
        // 此处尚未设置租户作用域上下文（应用可能注册在任意租户），按应用编码全局查询，显式声明系统上下文
        SysOpenApp app = TenantUtil.callAsSystem(() -> openAppService.getByCode(appCode));
        if (app != null && StringUtils.isNotBlank(app.getAllowedIps())) {
            String clientIp = getClientIp(request);
            List<String> allowedIps = JSON.parseArray(app.getAllowedIps(), String.class);
            if (allowedIps != null && !allowedIps.isEmpty() && !allowedIps.contains(clientIp)) {
                sendError(response, 403, "IP 不在白名单");
                return false;
            }
        }

        // 5. 限流
        if (app != null) {
            if (!rateLimiter.checkRateLimit(appCode, app.getRateLimit(), app.getDailyLimit())) {
                sendError(response, 429, "请求过于频繁");
                return false;
            }
        }

        // 6. 设置 OpenAppContext
        OpenAppContext.OpenAppInfo appInfo = new OpenAppContext.OpenAppInfo(
                appCode, tenantCode, grantedScopes, dataScopeType, dataScopeDeptIds, employeeId);
        OpenAppContext.set(appInfo);

        // 7. 统一设置租户作用域上下文：无论哪种授权模式，/open/** 的数据访问都收敛到应用所属租户
        //    （client_credentials 无代理用户时同样生效，避免 ORM 隔离整体失效）
        LoginUser scopeUser = new LoginUser();
        scopeUser.setEmployeeId(StringUtils.isNotBlank(employeeId) ? employeeId : OPEN_SYSTEM_EMPLOYEE_ID);
        scopeUser.setTenantCode(tenantCode);
        scopeUser.setSuperAdmin(false);
        scopeUser.setAdmin(false);
        LoginUserContext.set(scopeUser);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        try {
            // 记录调用审计日志
            OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
            if (appInfo != null) {
                String requestUri = request.getRequestURI();
                String apiPath = requestUri.startsWith(contextPath) ? requestUri.substring(contextPath.length()) : requestUri;
                long startTime = (long) request.getAttribute(START_TIME_ATTR);
                long duration = System.currentTimeMillis() - startTime;
                String params = request.getQueryString();
                String errorMsg = ex != null ? ex.getMessage() : null;
                String ip = getClientIp(request);

                openApiLogService.logAsync(
                        appInfo.getAppCode(),
                        apiPath,
                        request.getMethod(),
                        params,
                        response.getStatus(),
                        duration,
                        ip,
                        appInfo.getEmployeeId(),
                        errorMsg,
                        appInfo.getTenantCode()
                );
            }
        } finally {
            OpenAppContext.clear();
            LoginUserContext.clean();
        }
    }

    // ==================== Private ====================

    /**
     * 从路径 + HTTP 方法推断所需 scope。
     * <p>
     * GET /open/{module}/* → {module}:read
     * POST /open/{module}/* → {module}:write（特殊：msg → msg:send, dp → dp:check, lowcode → menu:write）
     * <p>
     * 例外：{@code /open/meta/*}（应用自描述，如资源目录）返回 null = 不要求 scope。
     * 该接口描述的是调用方自身可用的资源，若也要求 scope，就形成"想知道自己有哪些权限
     * 必须先拥有该权限"的循环；身份仍由前面的 Token 校验把关。
     */
    private String resolveScope(String apiPath, String method) {
        // 去掉 /open/ 前缀
        String path = apiPath.startsWith("/open/") ? apiPath.substring(6) : apiPath;
        String[] parts = path.split("/");
        if (parts.length < 1 || parts[0].isEmpty()) {
            return null;
        }
        String module = parts[0];
        if ("meta".equals(module)) {
            return null;
        }
        return switch (method.toUpperCase()) {
            // 低代码菜单同步本质是菜单读写，复用 menu scope，避免调用方重复申请 scope
            case "GET" -> "lowcode".equals(module) ? "menu:read" : module + ":read";
            case "POST" -> switch (module) {
                case "msg" -> "msg:send";
                case "dp" -> "dp:check";
                case "lowcode" -> "menu:write";
                default -> module + ":write";
            };
            case "PUT", "PATCH" -> module + ":write";
            case "DELETE" -> module + ":write";
            default -> null;
        };
    }

    /**
     * 客户端真实 IP（仅当直连方为可信代理时才采信 X-Forwarded-For / X-Real-IP）。
     */
    private String getClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private void sendError(HttpServletResponse response, int status, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(java.util.Map.of("code", status, "msg", msg)));
    }
}
