package com.nway.system.authorization.service.decision;

import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.authorization.service.grant.GrantCacheService;
import com.nway.system.authorization.service.grant.GrantScopeFilter;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.dao.ResourceDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 对外策略包（bundle）：必须与决策内核语义一致，且必须做租户收敛。
 * <p>
 * 回归重点：bundle 曾只按 appCode 过滤，跨租户授权会被下发到当前主体，
 * 进而被下游（wform / 外部 SDK）当作本租户权限使用。
 */
class AuthzBundleServiceTest {

    private final GrantCacheService grantCacheService = mock(GrantCacheService.class);
    private final AuthzDecisionService decisionService = mock(AuthzDecisionService.class);
    private final AuthzVersionService versionService = mock(AuthzVersionService.class);
    private final SubjectContextResolver subjectResolver = mock(SubjectContextResolver.class);
    private final ResourceDao resourceDao = mock(ResourceDao.class);

    private final AuthzBundleService service = new AuthzBundleService(
            grantCacheService, decisionService, versionService, subjectResolver,
            new GrantScopeFilter(resourceDao));

    private final SubjectContext subject =
            new SubjectContext("E1", "t1", null, false, List.of("D01"));

    @BeforeEach
    void setUp() {
        when(subjectResolver.fromEmployeeId("E1")).thenReturn(subject);
        when(versionService.current()).thenReturn(3L);
        // bundle 复用自己加载的 grants 做判定（不再每个 action 重新拉授权）
        when(decisionService.decideWith(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new AuthzDecisionService.Decision(true, "allow-grant", "d1",
                        List.of(new AuthzDecisionService.DataPredicate(SysGrant.SCOPE_SELF, List.of("E1"))),
                        List.of("g1"), 3L));
    }

    @Test
    @DisplayName("跨租户授权不下发：bundle 只包含主体当前租户的授权")
    void crossTenantGrantsExcluded() {
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g1", "t1", "form1", "[\"read\"]", SysGrant.EFFECT_ALLOW),
                grant("g2", "t2", "form2", "[\"read\"]", SysGrant.EFFECT_ALLOW),
                grant("g3", "public", "form3", "[\"read\"]", SysGrant.EFFECT_ALLOW)));

        AuthzBundleService.Bundle bundle = service.bundle("E1", "wform");

        assertThat(bundle.resources()).extracting(AuthzBundleService.ResourceGrant::resourceCode)
                .containsExactlyInAnyOrder("form1", "form3");
        assertThat(bundle.version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("DENY 生效：同租户 DENY 从动作集合中扣除")
    void denyRemovesAction() {
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g1", "t1", "form1", "[\"read\",\"write\"]", SysGrant.EFFECT_ALLOW),
                grant("g2", "t1", "form1", "[\"write\"]", SysGrant.EFFECT_DENY)));

        AuthzBundleService.Bundle bundle = service.bundle("E1", "wform");

        assertThat(bundle.resources()).hasSize(1);
        assertThat(bundle.resources().get(0).actions()).containsExactly("read");
    }

    @Test
    @DisplayName("数据谓词随 bundle 下发（供下游翻译，不做二次推导）")
    void predicatesIncluded() {
        when(grantCacheService.effectiveGrants("E1"))
                .thenReturn(List.of(grant("g1", "t1", "form1", "[\"read\"]", SysGrant.EFFECT_ALLOW)));

        AuthzBundleService.Bundle bundle = service.bundle("E1", "wform");

        assertThat(bundle.resources().get(0).predicates())
                .containsExactly(new AuthzDecisionService.DataPredicate(
                        SysGrant.SCOPE_SELF, List.of("E1")));
    }

    @Test
    @DisplayName("其他应用的授权不进入本应用策略包")
    void otherAppExcluded() {
        when(grantCacheService.effectiveGrants("E1"))
                .thenReturn(List.of(grant("g1", "t1", "form1", "[\"read\"]", SysGrant.EFFECT_ALLOW)));

        AuthzBundleService.Bundle bundle = service.bundle("E1", "another-app");

        assertThat(bundle.resources()).isEmpty();
    }

    @Test
    @DisplayName("超管下发通配资源与动作（与决策内核 allow-all 一致，SDK 能识别通配符）")
    void superAdminGetsWildcard() {
        SubjectContext admin = new SubjectContext("E9", "t1", null, true, List.of());
        when(subjectResolver.fromEmployeeId("E9")).thenReturn(admin);

        AuthzBundleService.Bundle bundle = service.bundle("E9", "wform");

        assertThat(bundle.resources()).hasSize(1);
        assertThat(bundle.resources().get(0).resourceCode()).isEqualTo("*");
        assertThat(bundle.resources().get(0).actions()).containsExactly("*");
        assertThat(bundle.resources().get(0).predicates())
                .containsExactly(new AuthzDecisionService.DataPredicate(SysGrant.SCOPE_ALL, List.of()));
    }

    @Test
    @DisplayName("DENY 通配动作直接封掉整个资源")
    void denyWildcardBlocksResource() {
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g1", "t1", "form1", "[\"read\",\"write\"]", SysGrant.EFFECT_ALLOW),
                grant("g2", "t1", "form1", "[\"*\"]", SysGrant.EFFECT_DENY)));

        AuthzBundleService.Bundle bundle = service.bundle("E1", "wform");

        assertThat(bundle.resources()).isEmpty();
    }

    @Test
    @DisplayName("单遍合并：DENY 先于 ALLOW 出现也生效（与顺序无关）")
    void denyBeforeAllowStillApplies() {
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g2", "t1", "form1", "[\"write\"]", SysGrant.EFFECT_DENY),
                grant("g1", "t1", "form1", "[\"read\",\"write\"]", SysGrant.EFFECT_ALLOW)));

        AuthzBundleService.Bundle bundle = service.bundle("E1", "wform");

        assertThat(bundle.resources()).hasSize(1);
        assertThat(bundle.resources().get(0).actions()).containsExactly("read");
    }

    private static SysGrant grant(String id, String tenantCode, String resourceCode,
                                  String actions, String effect) {
        SysGrant grant = new SysGrant();
        grant.setId(id);
        grant.setSubjectType("ROLE");
        grant.setSubjectId("r1");
        grant.setAppCode("wform");
        grant.setResourceCode(resourceCode);
        grant.setActions(actions);
        grant.setEffect(effect);
        grant.setDataScope(SysGrant.SCOPE_SELF);
        grant.setStatus(SysGrant.STATUS_ACTIVE);
        grant.setTenantCode(tenantCode);
        return grant;
    }
}
