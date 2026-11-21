package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.authorization.service.decision.AuthzDecisionService;
import com.nway.system.authorization.service.decision.SubjectContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 接口级权限判定。
 * <p>
 * 约定：接口资源编码为 {@code ai:{interfaceCode}}，动作 {@code execute}。
 * <ul>
 *   <li>{@code access_mode=PUBLIC}：对话入口已由拦截器校验，接口本身直接放行；</li>
 *   <li>{@code access_mode=RESTRICTED}：按 sys_grant 严格过滤（DENY 优先）；</li>
 *   <li>{@code ai:*}：作为“全部 AI 接口”分组授权参与匹配；</li>
 * </ul>
 * 仅超级管理员直通；平台管理员与普通用户一致，必须按 sys_grant 判定。
 *
 * @author nway
 */
@Service
public class AiPermissionService {

    private static final Logger log = LoggerFactory.getLogger(AiPermissionService.class);

    public static final String APP_CODE = "nway-system";

    public static final String ACTION = "execute";

    public static final String MODE_PUBLIC = "PUBLIC";

    public static final String MODE_RESTRICTED = "RESTRICTED";

    private final AuthzDecisionService authzDecisionService;

    public AiPermissionService(AuthzDecisionService authzDecisionService) {
        this.authzDecisionService = authzDecisionService;
    }

    /**
     * 判定结果：除是否放行外，保留原因/决策 id，供调用日志与管理端诊断使用。
     */
    public record PermissionDecision(boolean allowed, String reason, String decisionId,
                                     String resourceCode, String accessMode,
                                     List<String> matchedGrantIds) {
    }

    public boolean isInterfaceAllowed(AiUserContext user, AiInterfaceConfig config) {
        return evaluate(user, config).allowed();
    }

    public PermissionDecision evaluate(AiUserContext user, AiInterfaceConfig config) {
        if (config == null || StringUtils.isBlank(config.getInterfaceCode())) {
            return new PermissionDecision(false, "no-interface", null, null, null, List.of());
        }
        String resourceCode = AiResourceCodes.of(config.getInterfaceCode());
        String accessMode = StringUtils.defaultIfBlank(config.getAccessMode(), MODE_RESTRICTED)
                .toUpperCase(java.util.Locale.ROOT);
        if (user == null || StringUtils.isBlank(user.getEmployeeId())) {
            return new PermissionDecision(false, "no-user", null, resourceCode, accessMode, List.of());
        }
        if (user.isSuperAdmin()) {
            return new PermissionDecision(true, "super-admin", null, resourceCode, accessMode, List.of());
        }
        if (MODE_PUBLIC.equals(accessMode)) {
            return new PermissionDecision(true, "public", null, resourceCode, accessMode, List.of());
        }
        SubjectContext subject = new SubjectContext(user.getEmployeeId(), user.getTenantCode(),
                user.getWorkTenantCode(), false, user.getDeptCodes());
        AuthzDecisionService.Decision decision =
                authzDecisionService.decide(subject, APP_CODE, resourceCode, ACTION);
        if (!decision.allow()) {
            log.debug("AI 接口无调用权限 employeeId={}, interfaceCode={}, resourceCode={}, "
                            + "reason={}, decisionId={}, matchedGrantIds={}",
                    user.getEmployeeId(), config.getInterfaceCode(), resourceCode,
                    decision.reason(), decision.decisionId(), decision.matchedGrantIds());
        }
        return new PermissionDecision(decision.allow(), decision.reason(), decision.decisionId(),
                resourceCode, accessMode, decision.matchedGrantIds());
    }
}
