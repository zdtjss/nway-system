package com.nway.system.authorization.web.interceptor;

import com.nway.system.authorization.service.decision.AuthzDecisionService;
import com.nway.system.authorization.service.decision.SubjectContext;
import com.nway.system.authorization.service.decision.SubjectContextResolver;
import com.nway.system.authorization.service.resource.ResourceManifest;
import com.nway.system.authorization.service.resource.ResourceManifestProvider;
import com.nway.system.common.security.PublicApi;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.tenant.support.TenantQuotaGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 统一权限拦截器（服务端映射版）。
 * <p>
 * appCode + resourceCode 完全由服务端 {@link ApiResourceRegistry} 按 URL 路径解析，
 * 不再信任客户端请求头，避免"客户端不传权限标识即放行"的 fail-open。
 * <p>
 * 判定顺序：
 * <ol>
 *   <li>无登录上下文 → 401</li>
 *   <li>超管 → 放行</li>
 *   <li>标注 {@link PublicApi} 的 handler（登录用户通用接口）→ 放行</li>
 *   <li>路径未声明资源 → 403（fail-closed，新增受控接口须声明）</li>
 *   <li>交给唯一决策内核 {@link AuthzDecisionService}：DENY 优先、ALLOW 并集、数据谓词随决策下发</li>
 *   <li>候选动作为空（无法确定所需动作）→ 403（fail-closed）</li>
 * </ol>
 * 白名单路径（登录、当前用户菜单、开放接口等）由 WebMvcConfigurer 排除，不经过此拦截器。
 */
@Slf4j
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    /** 403 响应头：决策id，便于用户/管理员反馈问题 */
    public static final String DECISION_HEADER = "X-Decision-Id";

    @Autowired
    private AuthzDecisionService authzDecisionService;

    @Autowired
    private SubjectContextResolver subjectContextResolver;

    @Autowired
    private TenantQuotaGuard tenantQuotaGuard;

    @Autowired
    private ResourceManifestProvider resourceManifestProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LoginUser loginUser = LoginUserContext.current();
        if (loginUser == null) {
            response.setStatus(401);
            return false;
        }

        // 超管放行
        if (loginUser.isSuperAdmin()) {
            return true;
        }

        // 登录用户通用接口（当前用户菜单、消息轮询、字典通用查询等）
        if (isPublicApi(handler)) {
            return true;
        }

        // 租户级配额（防单一租户异常流量拖垮全局；未配置或内部异常时放行）
        if (!tenantQuotaGuard.tryAcquire(loginUser.getTenantCode())) {
            log.warn("租户 {} 触发请求配额限制", loginUser.getTenantCode());
            response.sendError(429, "请求过于频繁，请稍后重试");
            return false;
        }

        String servletPath = request.getServletPath();
        ResourceManifest.Entry entry = resourceManifestProvider.get().resolve(servletPath).orElse(null);
        if (entry == null || entry.source() == ResourceManifest.Source.PUBLIC) {
            // PUBLIC 条目通常已被 MVC 排除；未命中清单则 fail-closed（新增接口漏声明）
            if (entry != null) {
                return true;
            }
            log.warn("权限拒绝：接口未登记资源映射 path={} user={}", servletPath, loginUser.getEmployeeId());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "接口未授权，请联系管理员配置资源权限");
            return false;
        }
        if (!entry.covered()) {
            log.warn("权限拒绝：接口未声明资源 path={} user={}", servletPath, loginUser.getEmployeeId());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "接口未授权，请联系管理员配置资源权限");
            return false;
        }

        SubjectContext subject = subjectContextResolver.from(loginUser);
        List<String> candidates = entry.actions();
        // 无法确定所需动作 → fail-closed 拒绝（旧实现按"仅资源级"放行，属 fail-open 漏洞）
        if (candidates.isEmpty()) {
            log.warn("权限拒绝：无法确定 {} 的所需动作（method={}）user={}",
                    servletPath, request.getMethod(), loginUser.getEmployeeId());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "无权限访问");
            return false;
        }
        AuthzDecisionService.Decision last = null;
        for (String action : candidates) {
            last = authzDecisionService.decide(subject, entry.appCode(), entry.resourceCode(), action);
            if (last.allow()) {
                response.setHeader(DECISION_HEADER, last.decisionId());
                return true;
            }
        }
        if (last != null) {
            response.setHeader(DECISION_HEADER, last.decisionId());
        }
        log.debug("权限拒绝：用户={} 无 {}:{} 的 {} 操作权限（reason={}）",
                loginUser.getEmployeeId(), entry.appCode(), entry.resourceCode(), candidates,
                last == null ? "n/a" : last.reason());
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "无权限访问");
        return false;
    }

    /**
     * 是否为登录用户通用接口（类或方法标注 {@link PublicApi}）。
     */
    private boolean isPublicApi(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return handlerMethod.hasMethodAnnotation(PublicApi.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class);
    }
}