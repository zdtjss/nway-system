package com.nway.system.authorization.service.decision;

import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.authorization.service.grant.GrantCacheService;
import com.nway.system.authorization.service.grant.GrantScopeFilter;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 决策内核语义：DENY 优先、通配资源、数据谓词合并、超管放行。
 */
class AuthzDecisionServiceTest {

    private final GrantCacheService grantCacheService = mock(GrantCacheService.class);
    private final AuthzVersionService versionService = mock(AuthzVersionService.class);
    private final ResourceDao resourceDao = mock(ResourceDao.class);
    private final AuthzDecisionService service =
            new AuthzDecisionService(grantCacheService, versionService, new GrantScopeFilter(resourceDao));

    private final SubjectContext subject = new SubjectContext("E1", "t1", null, false, List.of("D01"));

    @Test
    @DisplayName("无授权：拒绝，理由 no-grant")
    void noGrantDenies() {
        when(versionService.current()).thenReturn(7L);
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of());

        AuthzDecisionService.Decision decision = service.decide(subject, "nway-system", "user", "view");

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("no-grant");
        assertThat(decision.version()).isEqualTo(7L);
        assertThat(decision.decisionId()).hasSize(16);
    }

    @Test
    @DisplayName("ALLOW 命中：放行并给出 SELF 数据谓词")
    void allowGrant() {
        when(versionService.current()).thenReturn(1L);
        when(grantCacheService.effectiveGrants("E1"))
                .thenReturn(List.of(grant("g1", "user", "[\"view\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_SELF)));

        AuthzDecisionService.Decision decision = service.decide(subject, "nway-system", "user", "view");

        assertThat(decision.allow()).isTrue();
        assertThat(decision.matchedGrantIds()).containsExactly("g1");
        assertThat(decision.dataPredicates()).containsExactly(
                new AuthzDecisionService.DataPredicate(SysGrant.SCOPE_SELF, List.of("E1")));
    }

    @Test
    @DisplayName("DENY 优先：同时存在 ALLOW 与 DENY 时拒绝，并给出命中授权")
    void denyOverridesAllow() {
        when(versionService.current()).thenReturn(1L);
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g1", "user", "[\"*\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_ALL),
                grant("g2", "user", "[\"delete\"]", SysGrant.EFFECT_DENY, SysGrant.SCOPE_NONE)));

        AuthzDecisionService.Decision delete = service.decide(subject, "nway-system", "user", "delete");
        AuthzDecisionService.Decision view = service.decide(subject, "nway-system", "user", "view");

        assertThat(delete.allow()).isFalse();
        assertThat(delete.reason()).isEqualTo("deny-grant");
        assertThat(delete.matchedGrantIds()).containsExactly("g2");
        assertThat(view.allow()).isTrue();
    }

    @Test
    @DisplayName("应用级通配资源：resource_code=* 覆盖该应用全部资源")
    void wildcardResource() {
        when(versionService.current()).thenReturn(1L);
        when(grantCacheService.effectiveGrants("E1"))
                .thenReturn(List.of(grant("g1", "*", "[\"view\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_ALL)));

        assertThat(service.decide(subject, "nway-system", "tenant", "view").allow()).isTrue();
        assertThat(service.decide(subject, "other-app", "tenant", "view").allow()).isFalse();
    }

    @Test
    @DisplayName("前缀通配资源：ai:* 只覆盖 ai: 前缀资源")
    void prefixWildcardResource() {
        when(versionService.current()).thenReturn(1L);
        when(grantCacheService.effectiveGrants("E1"))
                .thenReturn(List.of(grant("g1", "ai:*", "[\"execute\"]",
                        SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_ALL)));

        assertThat(service.decide(subject, "nway-system", "ai:user_query", "execute").allow()).isTrue();
        assertThat(service.decide(subject, "nway-system", "ai:user_query", "view").allow()).isFalse();
        assertThat(service.decide(subject, "nway-system", "ai_interface", "execute").allow()).isFalse();
        assertThat(service.decide(subject, "nway-system", "user", "execute").allow()).isFalse();
    }

    @Test
    @DisplayName("前缀通配 DENY 优先：ai:* 拒绝覆盖单接口 ALLOW")
    void prefixWildcardDenyWins() {
        when(versionService.current()).thenReturn(1L);
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g1", "ai:user_query", "[\"execute\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_ALL),
                grant("g2", "ai:*", "[\"execute\"]", SysGrant.EFFECT_DENY, SysGrant.SCOPE_NONE)));

        AuthzDecisionService.Decision decision =
                service.decide(subject, "nway-system", "ai:user_query", "execute");

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("deny-grant");
        assertThat(decision.matchedGrantIds()).containsExactly("g2");
    }

    @Test
    @DisplayName("数据谓词合并：任一条 ALL 则不限制")
    void allScopeWins() {
        when(versionService.current()).thenReturn(1L);
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g1", "user", "[\"view\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_SELF),
                grant("g2", "user", "[\"view\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_ALL)));

        AuthzDecisionService.Decision decision = service.decide(subject, "nway-system", "user", "view");

        assertThat(decision.dataPredicates()).containsExactly(
                new AuthzDecisionService.DataPredicate(SysGrant.SCOPE_ALL, List.of()));
    }

    @Test
    @DisplayName("数据谓词：DEPT_LIST 使用授权里的部门编码列表")
    void deptListScope() {
        when(versionService.current()).thenReturn(1L);
        SysGrant grant = grant("g1", "user", "[\"view\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_DEPT_LIST);
        grant.setScopeDeptIds("[\"D02\",\"D03\"]");
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(grant));

        AuthzDecisionService.Decision decision = service.decide(subject, "nway-system", "user", "view");

        assertThat(decision.dataPredicates())
                .containsExactly(new AuthzDecisionService.DataPredicate(
                        SysGrant.SCOPE_DEPT_LIST, List.of("D02", "D03")));
    }

    @Test
    @DisplayName("超管：直接 ALLOW(ALL)，不查询授权")
    void superAdmin() {
        SubjectContext admin = new SubjectContext("root", "t1", null, true, List.of());

        AuthzDecisionService.Decision decision = service.decide(admin, "nway-system", "user", "delete");

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("super-admin");
        assertThat(decision.dataPredicates()).containsExactly(
                new AuthzDecisionService.DataPredicate(SysGrant.SCOPE_ALL, List.of()));
    }

    @Test
    @DisplayName("租户收敛：TENANT 资源仅归属租户可用，跨租户管理员被拒绝")
    void tenantScopedResourceRejectedAcrossTenants() {
        when(versionService.current()).thenReturn(1L);
        SysGrant grant = grant("g1", "form1", "[\"view\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_SELF);
        grant.setTenantCode("t1");
        when(grantCacheService.effectiveGrants("E2")).thenReturn(List.of(grant));
        SysResource resource = new SysResource();
        resource.setAppCode("nway-system");
        resource.setResourceCode("form1");
        resource.setTenantScope(SysResource.SCOPE_TENANT);
        resource.setOwnerTenantCode("t1");
        when(resourceDao.getByAppAndCode(anyString(), anyString())).thenReturn(resource);

        SubjectContext userInT1 = new SubjectContext("E2", "t1", null, false, List.of());
        SubjectContext userInT2 = new SubjectContext("E2", "t2", null, false, List.of());

        assertThat(service.decide(userInT1, "nway-system", "form1", "view").allow()).isTrue();
        assertThat(service.decide(userInT2, "nway-system", "form1", "view").allow()).isFalse();
    }

    @Test
    @DisplayName("租户收敛：工作租户优先于归属租户")
    void workTenantTakesPrecedence() {
        when(versionService.current()).thenReturn(1L);
        SysGrant grant = grant("g1", "form1", "[\"view\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_SELF);
        grant.setTenantCode("t2");
        when(grantCacheService.effectiveGrants("E2")).thenReturn(List.of(grant));
        SysResource resource = new SysResource();
        resource.setAppCode("nway-system");
        resource.setResourceCode("form1");
        resource.setTenantScope(SysResource.SCOPE_TENANT);
        resource.setOwnerTenantCode("t2");
        when(resourceDao.getByAppAndCode(anyString(), anyString())).thenReturn(resource);

        SubjectContext workingInT2 = new SubjectContext("E2", "t1", "t2", false, List.of());

        assertThat(service.decide(workingInT2, "nway-system", "form1", "view").allow()).isTrue();
    }

    @Test
    @DisplayName("批量判定：只加载一次授权，逐项复用（菜单场景）")
    void decideBatchLoadsGrantsOnce() {
        when(versionService.current()).thenReturn(3L);
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g1", "form1", "[\"read\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_SELF),
                grant("g2", "form2", "[\"*\"]", SysGrant.EFFECT_DENY, SysGrant.SCOPE_ALL)));

        Set<String> allowed = service.decideBatch(subject, List.of(
                new AuthzDecisionService.Check("m1", "nway-system", "form1", null),
                new AuthzDecisionService.Check("m2", "nway-system", "form2", null),
                new AuthzDecisionService.Check("m3", "nway-system", "form3", null),
                new AuthzDecisionService.Check("m4", "nway-system", "form1", "read"),
                new AuthzDecisionService.Check("m5", "nway-system", "form1", "write")));

        assertThat(allowed).containsExactlyInAnyOrder("m1", "m4");
        verify(grantCacheService, times(1)).effectiveGrants("E1");
    }

    @Test
    @DisplayName("任意动作判定：同资源存在 DENY 时整体不可见")
    void allowsAnyDenyWins() {
        List<SysGrant> grants = List.of(
                grant("g1", "form1", "[\"read\"]", SysGrant.EFFECT_ALLOW, SysGrant.SCOPE_SELF),
                grant("g2", "form1", "[\"write\"]", SysGrant.EFFECT_DENY, SysGrant.SCOPE_SELF));

        assertThat(service.allowsAnyWith(subject, grants, "nway-system", "form1")).isFalse();
    }

    @Test
    @DisplayName("超管批量判定：全部放行且不回库加载授权")
    void superAdminBatchSkipsGrantLoad() {
        SubjectContext admin = new SubjectContext("E9", "t1", null, true, List.of());

        Set<String> allowed = service.decideBatch(admin, List.of(
                new AuthzDecisionService.Check("m1", "nway-system", "user", null),
                new AuthzDecisionService.Check("m2", "nway-system", "role", "write")));

        assertThat(allowed).containsExactly("m1", "m2");
        verify(grantCacheService, never()).effectiveGrants(anyString());
    }

    private static SysGrant grant(String id, String resource, String actions, String effect, String scope) {
        SysGrant grant = new SysGrant();
        grant.setId(id);
        grant.setSubjectType("ROLE");
        grant.setSubjectId("r1");
        grant.setAppCode("nway-system");
        grant.setResourceCode(resource);
        grant.setActions(actions);
        grant.setEffect(effect);
        grant.setDataScope(scope);
        grant.setStatus(SysGrant.STATUS_ACTIVE);
        return grant;
    }
}
