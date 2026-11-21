package com.nway.system.openplatform.service;

import com.nway.system.tenant.support.AdminGrantCacheManager;
import com.nway.system.tenant.web.model.AdminGrantDto;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 开放平台"可管理租户"解析单元测试。
 */
@DisplayName("OpenTenantScopeResolver：可管理租户口径")
class OpenTenantScopeResolverTest {

    private final UserDao userDao = mock(UserDao.class);
    private final AdminGrantCacheManager adminGrantCacheManager = mock(AdminGrantCacheManager.class);
    private final TenantDao tenantDao = mock(TenantDao.class);

    private OpenTenantScopeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OpenTenantScopeResolver();
        ReflectionTestUtils.setField(resolver, "userDao", userDao);
        ReflectionTestUtils.setField(resolver, "adminGrantCacheManager", adminGrantCacheManager);
        ReflectionTestUtils.setField(resolver, "tenantDao", tenantDao);
    }

    private static User user(String tenantCode, boolean superAdmin, boolean admin) {
        User u = new User();
        u.setEmployeeId("e001");
        u.setTenantCode(tenantCode);
        u.setIsSuperAdmin(superAdmin);
        u.setIsAdmin(admin);
        return u;
    }

    private static Tenant tenant(String code, String status) {
        Tenant t = new Tenant();
        t.setId(code + "-id");
        t.setTenantCode(code);
        t.setTenantName(code + "-name");
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("超管：不限制（返回 null，可管理任意启用租户）")
    void superAdminIsUnrestricted() {
        when(userDao.getByEmployeeId("e001")).thenReturn(user("a", true, false));
        when(tenantDao.queryAll()).thenReturn(List.of(tenant("a", "using"), tenant("b", "using")));

        assertThat(resolver.manageableTenants("e001")).isNull();
        assertThat(resolver.canManage("e001", "a")).isTrue();
        assertThat(resolver.canManage("e001", "b")).isTrue();
    }

    @Test
    @DisplayName("超管：目标租户不存在/未启用时拒绝（避免把无效租户写入 token 作用域）")
    void superAdminRejectsUnknownTenant() {
        when(userDao.getByEmployeeId("e001")).thenReturn(user("a", true, false));
        when(tenantDao.queryAll()).thenReturn(List.of(tenant("a", "using")));

        assertThat(resolver.canManage("e001", "a")).isTrue();
        assertThat(resolver.canManage("e001", "c")).isFalse();
    }

    @Test
    @DisplayName("管理员：所属租户 ∪ 授权租户")
    void adminIncludesGrantedTenants() {
        when(userDao.getByEmployeeId("e001")).thenReturn(user("a", false, true));
        AdminGrantDto grant = new AdminGrantDto();
        grant.setAdmin(true);
        grant.setTenantCodes(List.of("b", "c", "a"));
        when(adminGrantCacheManager.get("e001")).thenReturn(grant);

        assertThat(resolver.manageableTenants("e001")).containsExactly("a", "b", "c");
        assertThat(resolver.canManage("e001", "b")).isTrue();
        assertThat(resolver.canManage("e001", "d")).isFalse();
    }

    @Test
    @DisplayName("普通用户：仅所属租户")
    void normalUserOnlyOwnTenant() {
        when(userDao.getByEmployeeId("e001")).thenReturn(user("a", false, false));
        AdminGrantDto grant = new AdminGrantDto();
        grant.setAdmin(false);
        grant.setTenantCodes(List.of());
        when(adminGrantCacheManager.get("e001")).thenReturn(grant);

        assertThat(resolver.manageableTenants("e001")).containsExactly("a");
        assertThat(resolver.canManage("e001", "a")).isTrue();
        assertThat(resolver.canManage("e001", "b")).isFalse();
    }

    @Test
    @DisplayName("用户不存在：空集合（fail-closed）")
    void unknownUserHasNoTenant() {
        when(userDao.getByEmployeeId("ghost")).thenReturn(null);

        assertThat(resolver.manageableTenants("ghost")).isEmpty();
        assertThat(resolver.canManage("ghost", "a")).isFalse();
    }

    @Test
    @DisplayName("可管理租户明细：超管=全部启用租户；管理员=授权集合中的启用租户")
    void manageableTenantDetailsFiltersDisabled() {
        when(userDao.getByEmployeeId("e001")).thenReturn(user("a", true, false));
        when(tenantDao.queryAll()).thenReturn(List.of(tenant("a", "using"), tenant("b", "using")));
        List<TenantDto> all = resolver.manageableTenantDetails("e001");
        assertThat(all).extracting(TenantDto::getTenantCode).containsExactly("a", "b");

        when(userDao.getByEmployeeId("e002")).thenReturn(user("a", false, true));
        AdminGrantDto grant = new AdminGrantDto();
        grant.setAdmin(true);
        grant.setTenantCodes(List.of("b", "deleted"));
        when(adminGrantCacheManager.get("e002")).thenReturn(grant);
        when(tenantDao.queryByTenantCode(List.of("a", "b", "deleted")))
                .thenReturn(List.of(tenant("a", "using"), tenant("b", "using"), tenant("deleted", "disabled")));

        assertThat(resolver.manageableTenantDetails("e002"))
                .extracting(TenantDto::getTenantCode).containsExactly("a", "b");
    }
}