package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.authorization.service.decision.AuthzDecisionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口级权限：PUBLIC 直通，RESTRICTED 严格按 sys_grant 判定。
 */
class AiPermissionServiceTest {

    private final AuthzDecisionService decisionService = mock(AuthzDecisionService.class);
    private final AiPermissionService service = new AiPermissionService(decisionService);

    private final AiUserContext user = user("E1", false);

    @Test
    @DisplayName("PUBLIC：不查授权，直接放行")
    void publicAllowsWithoutDecision() {
        AiInterfaceConfig config = config("user_query", AiPermissionService.MODE_PUBLIC);

        assertThat(service.isInterfaceAllowed(user, config)).isTrue();
        verify(decisionService, never()).decide(any(), any(), any(), any());
    }

    @Test
    @DisplayName("RESTRICTED：无授权拒绝，并保留 reason/decisionId")
    void restrictedDeniesWithoutGrant() {
        AiInterfaceConfig config = config("user_query", AiPermissionService.MODE_RESTRICTED);
        when(decisionService.decide(any(), eq("nway-system"), eq("ai:user_query"), eq("execute")))
                .thenReturn(new AuthzDecisionService.Decision(false, "no-grant", "d1",
                        List.of(), List.of(), 1L));

        AiPermissionService.PermissionDecision decision = service.evaluate(user, config);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("no-grant");
        assertThat(decision.decisionId()).isEqualTo("d1");
        assertThat(decision.resourceCode()).isEqualTo("ai:user_query");
    }

    @Test
    @DisplayName("RESTRICTED：命中授权放行")
    void restrictedAllowsWithGrant() {
        AiInterfaceConfig config = config("user_query", AiPermissionService.MODE_RESTRICTED);
        when(decisionService.decide(any(), eq("nway-system"), eq("ai:user_query"), eq("execute")))
                .thenReturn(new AuthzDecisionService.Decision(true, "allow-grant", "d2",
                        List.of(), List.of("g1"), 1L));

        assertThat(service.isInterfaceAllowed(user, config)).isTrue();
    }

    @Test
    @DisplayName("超管：直接放行，不查授权")
    void superAdminBypassesDecision() {
        AiInterfaceConfig config = config("user_query", AiPermissionService.MODE_RESTRICTED);

        assertThat(service.isInterfaceAllowed(user("root", true), config)).isTrue();
        verify(decisionService, never()).decide(any(), any(), any(), any());
    }

    @Test
    @DisplayName("缺失 accessMode：按 RESTRICTED 处理（fail-closed）")
    void missingAccessModeIsRestricted() {
        AiInterfaceConfig config = config("user_query", null);
        when(decisionService.decide(any(), any(), any(), any()))
                .thenReturn(new AuthzDecisionService.Decision(false, "no-grant", "d3",
                        List.of(), List.of(), 1L));

        assertThat(service.isInterfaceAllowed(user, config)).isFalse();
    }

    private static AiInterfaceConfig config(String code, String accessMode) {
        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode(code);
        config.setAccessMode(accessMode);
        return config;
    }

    private static AiUserContext user(String employeeId, boolean superAdmin) {
        AiUserContext user = new AiUserContext();
        user.setEmployeeId(employeeId);
        user.setTenantCode("t1");
        user.setSuperAdmin(superAdmin);
        return user;
    }
}
