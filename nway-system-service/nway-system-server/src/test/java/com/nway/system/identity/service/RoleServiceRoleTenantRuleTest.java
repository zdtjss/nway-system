package com.nway.system.identity.service;

import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.identity.web.model.RoleDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色租户确定规则单元测试（方案 B：共享应用 + 各租户自建角色）。
 * <p>
 * 规则：
 * <ul>
 *   <li>私有应用 → 角色租户强制等于应用租户</li>
 *   <li>public 共享应用 → 保留调用方指定的租户（不得改写成 public）</li>
 *   <li>缺省租户 → 仅当当前可写租户唯一时自动取值，否则报错</li>
 *   <li>应用不存在 → fail-closed 拒绝</li>
 * </ul>
 */
@DisplayName("RoleService：角色租户规则")
class RoleServiceRoleTenantRuleTest {

    private final RoleDao roleDao = mock(RoleDao.class);
    private final AppDao appDao = mock(AppDao.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final com.nway.system.authorization.service.policy.PolicyService policyService =
            mock(com.nway.system.authorization.service.policy.PolicyService.class);

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService();
        ReflectionTestUtils.setField(service, "roleDao", roleDao);
        ReflectionTestUtils.setField(service, "appDao", appDao);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "policyService", policyService);
        // 真实环境由 nway-jdbc 的 StringIdStrategy 在 insert 时回填主键，mock 需自行补齐，
        // 否则 save() 里 Map.of("roleId", role.getId()) 会因 null 值抛 NPE
        doAnswer(invocation -> {
            ((Role) invocation.getArgument(0)).setId("r_test");
            return null;
        }).when(roleDao).insert(any(Role.class));
        LoginUserContext.clean();
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    private static RoleDto dto(String appCode, String tenantCode) {
        RoleDto dto = new RoleDto();
        dto.setRoleCode("wform_user");
        dto.setRoleName("表单中心用户");
        dto.setAppCode(appCode);
        dto.setTenantCode(tenantCode);
        return dto;
    }

    private static App app(String appCode, String tenantCode) {
        App app = new App();
        app.setAppCode(appCode);
        app.setAppName(appCode);
        app.setTenantCode(tenantCode);
        return app;
    }

    private static void loginAs(String tenantCode, boolean superAdmin) {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode(tenantCode);
        user.setSuperAdmin(superAdmin);
        user.setAdminTenantCodes(List.of());
        LoginUserContext.set(user);
    }

    private Role insertedRole() {
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleDao).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("私有应用：角色租户强制等于应用租户，覆盖传入值")
    void privateAppForcesAppTenant() {
        when(appDao.getByCode("sys_oa")).thenReturn(app("sys_oa", "OA"));
        loginAs("OA", false);

        service.save(dto("sys_oa", "default"));

        assertThat(insertedRole().getTenantCode()).isEqualTo("OA");
    }

    @Test
    @DisplayName("共享应用：租户自建角色，租户不被改写成 public")
    void sharedAppKeepsCallerTenant() {
        when(appDao.getByCode("wform")).thenReturn(app("wform", "public"));
        loginAs("OA", false);

        service.save(dto("wform", "OA"));

        assertThat(insertedRole().getTenantCode()).isEqualTo("OA");
    }

    @Test
    @DisplayName("共享应用 + 未传租户 + 可写租户唯一：自动取该租户")
    void sharedAppUsesUniqueWritableTenant() {
        when(appDao.getByCode("wform")).thenReturn(app("wform", "public"));
        loginAs("OA", false);

        service.save(dto("wform", null));

        assertThat(insertedRole().getTenantCode()).isEqualTo("OA");
    }

    @Test
    @DisplayName("共享应用 + 未传租户 + 超管：拒绝并提示显式指定租户")
    void sharedAppWithoutTenantForSuperAdminRejected() {
        when(appDao.getByCode("wform")).thenReturn(app("wform", "public"));
        loginAs("default", true);

        assertThatThrownBy(() -> service.save(dto("wform", null)))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("归属租户");
    }

    @Test
    @DisplayName("应用不存在：fail-closed 拒绝写入")
    void unknownAppRejected() {
        when(appDao.getByCode("ghost")).thenReturn(null);
        loginAs("OA", false);

        assertThatThrownBy(() -> service.save(dto("ghost", "OA")))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("必填兜底：角色名称为空白时拒绝写入（不产生脏数据）")
    void blankRoleNameRejected() {
        RoleDto dto = dto("*", "OA");
        dto.setRoleName("   ");

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("角色名称不能为空");
        verify(roleDao, org.mockito.Mockito.never()).insert(any(Role.class));
    }

    @Test
    @DisplayName("必填兜底：角色编码为 null 时拒绝写入（不产生脏数据）")
    void nullRoleCodeRejected() {
        RoleDto dto = dto("*", "OA");
        dto.setRoleCode(null);

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("角色编码不能为空");
        verify(roleDao, org.mockito.Mockito.never()).insert(any(Role.class));
    }

    @Test
    @DisplayName("平台级角色（app_code='*'）：保留传入租户，不做应用推导")
    void wildcardAppKeepsTenant() {
        loginAs("default", true);

        service.save(dto("*", "OA"));

        assertThat(insertedRole().getTenantCode()).isEqualTo("OA");
    }

    @Test
    @DisplayName("更新：归属租户不可变更，以库中记录为准")
    void updateKeepsExistingTenant() {
        when(appDao.getByCode("wform")).thenReturn(app("wform", "public"));
        loginAs("default", true);
        Role existing = new Role();
        existing.setId("r1");
        existing.setTenantCode("OA");
        when(roleDao.queryById("r1")).thenReturn(existing);

        RoleDto dto = dto("wform", "default");
        dto.setId("r1");
        service.save(dto);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleDao).updateById(captor.capture());
        assertThat(captor.getValue().getTenantCode()).isEqualTo("OA");
    }
}
