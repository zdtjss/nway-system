package com.nway.system.authz.sdk.interceptor;

import com.nway.system.authz.sdk.annotation.RequiresPermission;
import com.nway.system.authz.sdk.client.AuthzBundleClient;
import com.nway.system.authz.sdk.client.SubjectResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 接口级授权拦截器：标注 {@link RequiresPermission} 的端点由平台策略判定。
 * <p>
 * 失败姿态：无主体 → 401；无权限 → 403（响应头 {@code X-Authz-Reason} 便于排查）。
 * 未标注的端点不拦截（SDK 不擅自扩大保护面，接入方按需标注）。
 */
public class AuthzInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthzInterceptor.class);

    public static final String REASON_HEADER = "X-Authz-Reason";

    private final AuthzBundleClient client;
    private final SubjectResolver subjectResolver;

    public AuthzInterceptor(AuthzBundleClient client, SubjectResolver subjectResolver) {
        this.client = client;
        this.subjectResolver = subjectResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }
        if (required == null) {
            return true;
        }
        String employeeId = subjectResolver.resolveEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "未认证");
            return false;
        }
        boolean allowed = client.allows(employeeId, required.resource(), required.action());
        if (!allowed) {
            response.setHeader(REASON_HEADER,
                    required.resource() + ":" + required.action());
            log.debug("接口授权拒绝: employee={}, resource={}, action={}",
                    employeeId, required.resource(), required.action());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "无权限访问");
            return false;
        }
        return true;
    }
}
