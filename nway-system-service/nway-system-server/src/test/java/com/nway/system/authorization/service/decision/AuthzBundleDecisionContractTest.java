package com.nway.system.authorization.service.decision;

import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.authorization.service.grant.GrantCacheService;
import com.nway.system.authorization.service.grant.GrantScopeFilter;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.dao.ResourceDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B-5：策略包（bundle）与决策内核（decide）的一致性契约。
 * <p>
 * 两者是同一授权的两种交付方式：bundle 给外部 SDK 本地判定，decide 给服务端即时判定。
 * 一旦口径分歧，同一个用户会出现“菜单看得到、接口却 403”（或反之）。
 * <p>
 * 这里用真实决策内核跑一组覆盖了 DENY / 跨租户 / 通配资源 / 通配动作 的授权，
 * 对每个 (resource, action) 组合比较两个实现的结果——<b>必须逐项一致</b>。
 */
class AuthzBundleDecisionContractTest {

    private final GrantCacheService grantCacheService = mock(GrantCacheService.class);
    private final AuthzVersionService versionService = mock(AuthzVersionService.class);
    private final SubjectContextResolver subjectResolver = mock(SubjectContextResolver.class);
    private final ResourceDao resourceDao = mock(ResourceDao.class);

    private final GrantScopeFilter scopeFilter = new GrantScopeFilter(resourceDao);
    private final AuthzDecisionService decisionService =
            new AuthzDecisionService(grantCacheService, versionService, scopeFilter);
    private final AuthzBundleService bundleService =
            new AuthzBundleService(grantCacheService, decisionService, versionService,
                    subjectResolver, scopeFilter);

    private static final String APP = "wform";
    private static final List<String> RESOURCES = List.of("form1", "form2", "form3");
    private static final List<String> ACTIONS = List.of("read", "write", "delete");

    @Test
    @DisplayName("普通用户：bundle 与 decide 在每个 (resource, action) 上逐项一致")
    void bundleMatchesDecision() {
        SubjectContext subject = new SubjectContext("E1", "t1", null, false, List.of("D01"));
        when(subjectResolver.fromEmployeeId("E1")).thenReturn(subject);
        when(versionService.current()).thenReturn(11L);
        when(grantCacheService.effectiveGrants("E1")).thenReturn(List.of(
                grant("g1", "t1", "form1", "[\"read\",\"write\"]", SysGrant.EFFECT_ALLOW),
                grant("g2", "t1", "form1", "[\"write\"]", SysGrant.EFFECT_DENY),
                grant("g3", "public", "form2", "[\"read\"]", SysGrant.EFFECT_ALLOW),
                grant("g4", "t2", "form3", "[\"read\"]", SysGrant.EFFECT_ALLOW),
                grant("g5", "t1", "*", "[\"read\"]", SysGrant.EFFECT_ALLOW)));

        AuthzBundleService.Bundle bundle = bundleService.bundle("E1", APP);

        int allowed = 0;
        for (String resource : RESOURCES) {
            for (String action : ACTIONS) {
                boolean fromBundle = bundleAllows(bundle, resource, action);
                boolean fromDecision = decisionService
                        .decide(subject, APP, resource, action).allow();
                assertThat(fromBundle)
                        .as("bundle 与 decide 必须一致: %s:%s", resource, action)
                        .isEqualTo(fromDecision);
                if (fromDecision) {
                    allowed++;
                }
            }
        }
        assertThat(allowed).isGreaterThan(0);
        // 跨租户授权不得进入 bundle：form3 不应有「精确条目」
        // （本组数据里还有资源通配 "*" 的 read 授权，按 SDK 匹配规则会覆盖 form3，属预期）
        assertThat(bundle.resources())
                .extracting(AuthzBundleService.ResourceGrant::resourceCode)
                .doesNotContain("form3");
        // DENY 覆盖：form1:write 被拒，form1:read 保留
        assertThat(bundleAllows(bundle, "form1", "write")).isFalse();
        assertThat(bundleAllows(bundle, "form1", "read")).isTrue();
    }

    @Test
    @DisplayName("超管：bundle 与 decide 都放行任意资源（含未登记资源）")
    void superAdminMatchesDecision() {
        SubjectContext admin = new SubjectContext("E9", "t1", null, true, List.of());
        when(subjectResolver.fromEmployeeId("E9")).thenReturn(admin);
        when(versionService.current()).thenReturn(12L);

        AuthzBundleService.Bundle bundle = bundleService.bundle("E9", APP);

        List<String> probes = new ArrayList<>(List.of("form1", "form2", "never-registered"));
        for (String resource : probes) {
            for (String action : ACTIONS) {
                assertThat(bundleAllows(bundle, resource, action))
                        .as("超管 bundle 应放行: %s:%s", resource, action)
                        .isTrue();
                assertThat(decisionService.decide(admin, APP, resource, action).allow()).isTrue();
            }
        }
    }

    /** 与 SDK AuthzEvaluator 相同的匹配规则（精确资源优先，其次通配资源；动作支持通配）。 */
    private static boolean bundleAllows(AuthzBundleService.Bundle bundle, String resource, String action) {
        return bundle.resources().stream().anyMatch(grant ->
                (resource.equals(grant.resourceCode()) || "*".equals(grant.resourceCode()))
                        && (grant.actions().contains("*") || grant.actions().contains(action)));
    }

    private static SysGrant grant(String id, String tenantCode, String resourceCode,
                                  String actions, String effect) {
        SysGrant grant = new SysGrant();
        grant.setId(id);
        grant.setSubjectType("ROLE");
        grant.setSubjectId("r1");
        grant.setAppCode(APP);
        grant.setResourceCode(resourceCode);
        grant.setActions(actions);
        grant.setEffect(effect);
        grant.setDataScope(SysGrant.SCOPE_SELF);
        grant.setStatus(SysGrant.STATUS_ACTIVE);
        grant.setTenantCode(tenantCode);
        return grant;
    }
}
