package com.nway.system.audit.support;

import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.audit.dao.AuditLogDao;
import com.nway.system.audit.entity.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 写操作审计切面：拦截管理端 Controller 的 POST/PUT/DELETE 请求，将「谁在什么时间调用了哪个接口、
 * 结果如何」落库到 {@code sys_audit_log}。
 * <p>
 * 设计约束：
 * <ul>
 *   <li>只记录已登录用户的操作（匿名登录请求不落库，避免噪音与凭据泄露面）；</li>
 *   <li>请求参数只记录 query string 并做敏感关键字掩码（密码/密钥/令牌），<b>不读取 JSON body</b>
 *       ——body 已被 MVC 消费，且含敏感数据的风险高于审计收益；</li>
 *   <li>审计写入失败绝不影响业务主流程（try-catch + 告警日志）；</li>
 *   <li>操作名取「控制器.方法」，便于按业务动作检索（operationType）。</li>
 * </ul>
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    /** 敏感查询参数掩码（避免 access_token/password 等落入审计表）。 */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|key|authorization)=([^&]*)");

    private static final int MAX_PARAM_LENGTH = 2000;
    private static final int MAX_UA_LENGTH = 200;

    private final AuditLogDao auditLogDao;

    public AuditLogAspect(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController) && ("
            + "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                write(pjp, start, error);
            } catch (Exception e) {
                // 审计失败不得影响业务
                log.warn("审计日志写入失败（已忽略）：{}", e.getMessage());
            }
        }
    }

    private void write(ProceedingJoinPoint pjp, long startMillis, Throwable error) {
        LoginUser user = LoginUserContext.current();
        if (user == null || user.getEmployeeId() == null) {
            return; // 未登录/匿名请求不记审计
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String operation = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        AuditLog entry = new AuditLog();
        entry.setOperatorId(truncate(user.getEmployeeId(), 64));
        entry.setOperatorName(truncate(user.getRealName(), 50));
        entry.setOperationType(truncate(operation, 100));
        entry.setHttpMethod(request.getMethod());
        entry.setApiPath(truncate(request.getRequestURI(), 256));
        entry.setRequestParams(mask(request.getQueryString()));
        entry.setResponseStatus(error == null ? 200 : 500);
        entry.setDurationMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startMillis));
        entry.setIp(truncate(clientIp(request), 64));
        entry.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_UA_LENGTH));
        entry.setTenantCode(truncate(TenantUtil.currentTenantCode(), 20));
        entry.setCreateTime(LocalDateTime.now());
        auditLogDao.insert(entry);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes servletAttrs ? servletAttrs.getRequest() : null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String mask(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return truncate(SENSITIVE.matcher(query).replaceAll("$1=***"), MAX_PARAM_LENGTH);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
