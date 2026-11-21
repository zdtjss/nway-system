package com.nway.system.authorization.service.grant;

import com.nway.system.authorization.service.decision.SubjectContext;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 授权作用域收敛：授权归属租户 + 资源租户作用域 + 工作租户优先。
 * <p>
 * 这是"跨租户越权"的唯一防线，决策内核与对外策略包都必须经过它，
 * 因此单独固化语义，避免回归时被改回"只比较 appCode"。
 */
class GrantScopeFilterTest {

    private final ResourceDao resourceDao = mock(ResourceDao.class);
    private final GrantScopeFilter filter = new GrantScopeFilter(resourceDao);

    private final SubjectContext subjectInT1 =
            new SubjectContext("E1", "t1", null, false, List.of("D01"));

    @Test
    @DisplayName("授权租户 = 主体归属租户：适用")
    void sameTenantApplies() {
        assertThat(filter.applies(grant("t1", "form1"), subjectInT1)).isTrue();
    }

    @Test
    @DisplayName("授权租户 ≠ 主体归属租户：不适用（跨租户隔离）")
    void otherTenantRejected() {
        assertThat(filter.applies(grant("t2", "form1"), subjectInT1)).isFalse();
    }

    @Test
    @DisplayName("public 租户视为平台级授权：对所有租户适用")
    void publicTenantAppliesEverywhere() {
        assertThat(filter.applies(grant("public", "form1"), subjectInT1)).isTrue();
    }

    @Test
    @DisplayName("授权租户为空：视为平台级授权")
    void blankTenantApplies() {
        assertThat(filter.applies(grant(null, "form1"), subjectInT1)).isTrue();
    }

    @Test
    @DisplayName("TENANT 作用域资源：仅归属租户可用")
    void tenantScopedResourceRejectedAcrossTenants() {
        when(resourceDao.getByAppAndCode(anyString(), anyString()))
                .thenReturn(resource(SysResource.SCOPE_TENANT, "t1"));

        SubjectContext userInT2 = new SubjectContext("E1", "t2", null, false, List.of());
        assertThat(filter.applies(grant("t1", "form1"), subjectInT1)).isTrue();
        assertThat(filter.applies(grant("t2", "form1"), userInT2)).isFalse();
    }

    @Test
    @DisplayName("工作租户优先于归属租户：切到 t2 办公时 t1 的授权不生效")
    void workTenantTakesPrecedence() {
        SubjectContext workingInT2 = new SubjectContext("E1", "t1", "t2", false, List.of());

        assertThat(filter.effectiveTenant(workingInT2)).isEqualTo("t2");
        assertThat(filter.applies(grant("t1", "form1"), workingInT2)).isFalse();
        assertThat(filter.applies(grant("t2", "form1"), workingInT2)).isTrue();
    }

    @Test
    @DisplayName("未登记资源不做作用域限制（历史/通配授权）")
    void unknownResourceNotRestricted() {
        when(resourceDao.getByAppAndCode(anyString(), anyString())).thenReturn(null);

        assertThat(filter.applies(grant("t1", "*"), subjectInT1)).isTrue();
    }

    private static SysGrant grant(String tenantCode, String resourceCode) {
        SysGrant grant = new SysGrant();
        grant.setId("g1");
        grant.setSubjectType("ROLE");
        grant.setSubjectId("r1");
        grant.setAppCode("wform");
        grant.setResourceCode(resourceCode);
        grant.setActions("[\"read\"]");
        grant.setEffect(SysGrant.EFFECT_ALLOW);
        grant.setDataScope(SysGrant.SCOPE_SELF);
        grant.setStatus(SysGrant.STATUS_ACTIVE);
        grant.setTenantCode(tenantCode);
        return grant;
    }

    private static SysResource resource(String tenantScope, String ownerTenantCode) {
        SysResource resource = new SysResource();
        resource.setAppCode("wform");
        resource.setResourceCode("form1");
        resource.setTenantScope(tenantScope);
        resource.setOwnerTenantCode(ownerTenantCode);
        return resource;
    }
}
