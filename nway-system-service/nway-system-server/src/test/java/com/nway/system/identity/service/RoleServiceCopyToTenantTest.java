package com.nway.system.identity.service;

import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.identity.web.model.RoleDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.authorization.entity.PermissionPolicy;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色复制到租户单元测试（方案 B 新租户开通支撑）。
 */
@DisplayName("RoleService：复制角色到租户")
class RoleServiceCopyToTenantTest {

    private final RoleDao roleDao = mock(RoleDao.class);
    private final AppDao appDao = mock(AppDao.class);
    private final PolicyService policyService = mock(PolicyService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService();
        ReflectionTestUtils.setField(service, "roleDao", roleDao);
        ReflectionTestUtils.setField(service, "appDao", appDao);
        ReflectionTestUtils.setField(service, "policyService", policyService);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        // nway-jdbc 的 StringIdStrategy 在真实 insert 时回填主键，mock 需自行补齐
        doAnswer(invocation -> {
            ((Role) invocation.getArgument(0)).setId("r_copy");
            return null;
        }).when(roleDao).insert(any(Role.class));
        loginAs("default", true);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    private static void loginAs(String tenantCode, boolean superAdmin) {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode(tenantCode);
        user.setSuperAdmin(superAdmin);
        user.setAdminTenantCodes(List.of());
        LoginUserContext.set(user);
    }

    private static Role role(String id, String appCode, String tenantCode) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode("wform_user");
        role.setRoleName("表单中心用户");
        role.setAppCode(appCode);
        role.setTenantCode(tenantCode);
        return role;
    }

    private static App app(String appCode, String tenantCode) {
        App app = new App();
        app.setAppCode(appCode);
        app.setTenantCode(tenantCode);
        return app;
    }

    @Test
    @DisplayName("共享应用角色：复制到目标租户，编码/应用保留，策略重指到新角色")
    void copiesSharedAppRoleWithPolicies() {
        when(roleDao.queryById("r_src")).thenReturn(role("r_src", "wform", "OA"));
        when(appDao.getByCode("wform")).thenReturn(app("wform", "public"));
        when(roleDao.queryByTenantCodes(List.of("acme"))).thenReturn(List.of());
        PermissionPolicy src = new PermissionPolicy();
        src.setAppCode("wform");
        src.setResourceCode("forms");
        src.setActions("[\"view\"]");
        when(policyService.queryByRole("r_src")).thenReturn(List.of(src));

        RoleDto result = service.copyToTenant("r_src", "acme");

        assertThat(result.getId()).isEqualTo("r_copy");
        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleDao).insert(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getTenantCode()).isEqualTo("acme");
        assertThat(roleCaptor.getValue().getAppCode()).isEqualTo("wform");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PermissionPolicy>> polCaptor = ArgumentCaptor.forClass(List.class);
        verify(policyService).saveRolePolicies(eq("r_copy"), polCaptor.capture());
        assertThat(polCaptor.getValue()).hasSize(1);
        assertThat(polCaptor.getValue().get(0).getResourceCode()).isEqualTo("forms");
        assertThat(polCaptor.getValue().get(0).getActions()).isEqualTo("[\"view\"]");
    }

    @Test
    @DisplayName("源租户私有应用策略：目标租户不可达，复制时跳过而非整单失败")
    void skipsSourceTenantPrivateAppPolicies() {
        when(roleDao.queryById("r_src")).thenReturn(role("r_src", "wform", "OA"));
        when(appDao.getByCode("wform")).thenReturn(app("wform", "public"));
        when(appDao.getByCode("oaapp")).thenReturn(app("oaapp", "OA"));
        when(roleDao.queryByTenantCodes(List.of("acme"))).thenReturn(List.of());
        PermissionPolicy shared = new PermissionPolicy();
        shared.setAppCode("wform");
        shared.setResourceCode("forms");
        PermissionPolicy tenantPrivate = new PermissionPolicy();
        tenantPrivate.setAppCode("oaapp");
        tenantPrivate.setResourceCode("oaapp:leave:list");
        when(policyService.queryByRole("r_src")).thenReturn(List.of(shared, tenantPrivate));

        service.copyToTenant("r_src", "acme");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PermissionPolicy>> polCaptor = ArgumentCaptor.forClass(List.class);
        verify(policyService).saveRolePolicies(eq("r_copy"), polCaptor.capture());
        assertThat(polCaptor.getValue()).hasSize(1);
        assertThat(polCaptor.getValue().get(0).getAppCode()).isEqualTo("wform");
    }

    @Test
    @DisplayName("私有应用角色：不允许复制到其他租户")
    void privateAppRoleRejected() {
        when(roleDao.queryById("r_src")).thenReturn(role("r_src", "sys_oa", "OA"));
        when(appDao.getByCode("sys_oa")).thenReturn(app("sys_oa", "OA"));

        assertThatThrownBy(() -> service.copyToTenant("r_src", "acme"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("私有应用角色不能复制");
    }

    @Test
    @DisplayName("目标租户已存在同应用同编码角色：拒绝")
    void duplicateRejected() {
        when(roleDao.queryById("r_src")).thenReturn(role("r_src", "wform", "OA"));
        when(appDao.getByCode("wform")).thenReturn(app("wform", "public"));
        when(roleDao.queryByTenantCodes(List.of("acme"))).thenReturn(List.of(role("r_x", "wform", "acme")));

        assertThatThrownBy(() -> service.copyToTenant("r_src", "acme"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("已存在同应用同编码");
    }

    @Test
    @DisplayName("目标租户与源角色相同：拒绝")
    void sameTenantRejected() {
        when(roleDao.queryById("r_src")).thenReturn(role("r_src", "wform", "OA"));

        assertThatThrownBy(() -> service.copyToTenant("r_src", "OA"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("无需复制");
    }

    @Test
    @DisplayName("源角色不存在或无权限：拒绝")
    void unknownSourceRejected() {
        when(roleDao.queryById("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.copyToTenant("ghost", "acme"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("源角色不存在");
    }
}
