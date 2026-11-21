package com.nway.system.tenant.service;

import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务下拉租户范围单元测试。
 * <p>
 * 背景：角色管理页原直接调用 tenant/queryAll（tenant 权限），租户管理员无租户管理权限时
 * 403 会拖垮列表首屏；改为 role/tenantOptions 后须按调用方身份收敛。
 */
@DisplayName("TenantService：可管理租户范围")
class TenantServiceQueryManageableTest {

    private final TenantDao tenantDao = mock(TenantDao.class);

    private TenantService service;

    @BeforeEach
    void setUp() {
        service = new TenantService();
        ReflectionTestUtils.setField(service, "tenantDao", tenantDao);
        LoginUserContext.clean();
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    private static Tenant tenant(String code, String status) {
        Tenant tenant = new Tenant();
        tenant.setTenantCode(code);
        tenant.setTenantName(code);
        tenant.setStatus(status);
        return tenant;
    }

    private static void login(String tenantCode, boolean superAdmin, boolean admin, List<String> adminTenants) {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode(tenantCode);
        user.setSuperAdmin(superAdmin);
        user.setAdmin(admin);
        user.setAdminTenantCodes(adminTenants);
        LoginUserContext.set(user);
    }

    @Test
    @DisplayName("超管：返回全部启用租户")
    void superAdminGetsAllEnabled() {
        login("default", true, false, List.of());
        when(tenantDao.queryAll()).thenReturn(List.of(tenant("A", "using"), tenant("B", "using")));

        List<TenantDto> result = service.queryManageable();

        assertThat(result).extracting(TenantDto::getTenantCode).containsExactly("A", "B");
    }

    @Test
    @DisplayName("管理员：仅返回授权租户中的启用项")
    void adminGetsGrantedEnabledOnly() {
        login("A", false, true, List.of("A", "B"));
        when(tenantDao.queryByTenantCode(List.of("A", "B")))
                .thenReturn(List.of(tenant("A", "using"), tenant("B", "disabled")));

        List<TenantDto> result = service.queryManageable();

        assertThat(result).extracting(TenantDto::getTenantCode).containsExactly("A");
        verify(tenantDao, never()).queryAll();
    }

    @Test
    @DisplayName("普通用户：仅返回本租户")
    void normalUserGetsOwnTenant() {
        login("A", false, false, List.of());
        when(tenantDao.queryByTenantCode(List.of("A"))).thenReturn(List.of(tenant("A", "using")));

        List<TenantDto> result = service.queryManageable();

        assertThat(result).extracting(TenantDto::getTenantCode).containsExactly("A");
    }
}
