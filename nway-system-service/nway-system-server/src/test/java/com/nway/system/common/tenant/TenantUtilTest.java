package com.nway.system.common.tenant;

import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 租户上下文工具测试：隔离集合、写权限守卫、异步上下文传递。
 */
class TenantUtilTest {

    @AfterEach
    void cleanup() {
        LoginUserContext.clean();
    }

    private void loginAs(String tenantCode, boolean superAdmin, boolean admin, List<String> adminTenants) {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode(tenantCode);
        user.setSuperAdmin(superAdmin);
        user.setAdmin(admin);
        user.setAdminTenantCodes(adminTenants);
        LoginUserContext.set(user);
    }

    @Test
    @DisplayName("隔离集合：普通用户 = 本租户 + public")
    void isolationSetForNormalUser() {
        loginAs("t1", false, false, null);
        assertEquals(List.of("t1", TenantUtil.PUBLIC_TENANT_CODE), TenantUtil.currentTenantCodesForIsolation());
    }

    @Test
    @DisplayName("隔离集合：超管为 null（不隔离）")
    void isolationSetForSuperAdmin() {
        loginAs("t1", true, false, null);
        assertNull(TenantUtil.currentTenantCodesForIsolation());
    }

    @Test
    @DisplayName("可管理集合不含 public（读共享不等于写授权）")
    void manageableSetExcludesPublic() {
        loginAs("t1", false, true, List.of("t2"));
        List<String> manageable = TenantUtil.currentManageableTenantCodes();
        assertNotNull(manageable);
        assertEquals(List.of("t1", "t2"), manageable);
        assertFalse(manageable.contains(TenantUtil.PUBLIC_TENANT_CODE));
    }

    @Test
    @DisplayName("可管理集合：管理员指定工作租户时收敛到工作租户")
    void manageableSetNarrowedByWorkTenant() {
        LoginUser user = new LoginUser();
        user.setTenantCode("t1");
        user.setAdmin(true);
        user.setAdminTenantCodes(List.of("t2", "t3"));
        user.setCurrentWorkTenantCode("t2");
        LoginUserContext.set(user);

        assertEquals(List.of("t2"), TenantUtil.currentManageableTenantCodes());
    }

    @Test
    @DisplayName("写守卫：管理员未授权租户时拒绝")
    void assertManageTenantDeniedForUnauthorizedAdmin() {
        loginAs("t1", false, true, List.of("t2"));
        assertDoesNotThrow(() -> TenantUtil.assertAdminCanManageTenant("t2"));
        assertThrows(NwaySystemException.class, () -> TenantUtil.assertAdminCanManageTenant("t9"));
        assertThrows(NwaySystemException.class, () -> TenantUtil.assertAdminCanManageTenant(null));
    }

    @Test
    @DisplayName("写守卫：超管不受租户限制（含 public）")
    void assertManageTenantForSuperAdmin() {
        loginAs("t1", true, false, null);
        assertDoesNotThrow(() -> TenantUtil.assertAdminCanManageTenant("t9"));
        assertDoesNotThrow(() -> TenantUtil.assertAdminCanManageTenant(TenantUtil.PUBLIC_TENANT_CODE));
    }

    @Test
    @DisplayName("写守卫：普通用户只能写本租户（堵 UPDATE 改租户搬走自有行）")
    void assertManageTenantForNormalUserLimitedToOwnTenant() {
        loginAs("t1", false, false, null);
        assertDoesNotThrow(() -> TenantUtil.assertAdminCanManageTenant("t1"));
        assertThrows(NwaySystemException.class, () -> TenantUtil.assertAdminCanManageTenant("t9"));
        assertThrows(NwaySystemException.class,
                () -> TenantUtil.assertAdminCanManageTenant(TenantUtil.PUBLIC_TENANT_CODE));
        assertThrows(NwaySystemException.class, () -> TenantUtil.assertAdminCanManageTenant(null));
    }

    @Test
    @DisplayName("写守卫：管理员须显式授权 public 才能写公共租户资源本体")
    void assertManagePublicRequiresGrant() {
        loginAs("t1", false, true, List.of("t2"));
        assertThrows(NwaySystemException.class,
                () -> TenantUtil.assertAdminCanManageTenant(TenantUtil.PUBLIC_TENANT_CODE));

        loginAs("t1", false, true, List.of("t2", TenantUtil.PUBLIC_TENANT_CODE));
        assertDoesNotThrow(() -> TenantUtil.assertAdminCanManageTenant(TenantUtil.PUBLIC_TENANT_CODE));
    }

    @Test
    @DisplayName("引用守卫：public 共享资源放行，其余仍校验管理权")
    void referenceGuardAllowsPublic() {
        loginAs("t1", false, true, List.of("t2"));
        assertDoesNotThrow(() -> TenantUtil.assertAdminCanReferenceTenantResource(TenantUtil.PUBLIC_TENANT_CODE));
        assertDoesNotThrow(() -> TenantUtil.assertAdminCanReferenceTenantResource("t2"));
        assertThrows(NwaySystemException.class,
                () -> TenantUtil.assertAdminCanReferenceTenantResource("t9"));
    }

    @Test
    @DisplayName("runAsTenant：执行期间收敛到指定租户，结束后恢复原上下文")
    void runAsTenantScopesAndRestores() {
        loginAs("t1", false, false, null);

        TenantUtil.runAsTenant("t2", () -> {
            assertEquals("t2", TenantUtil.currentTenantCode());
            assertEquals(TenantUtil.SYSTEM_EMPLOYEE_ID, LoginUserContext.current().getEmployeeId());
        });

        assertEquals("t1", TenantUtil.currentTenantCode());
        assertEquals("e001", LoginUserContext.current().getEmployeeId());
    }

    @Test
    @DisplayName("runAsTenant：原上下文为空时执行后清理上下文")
    void runAsTenantCleansWhenNoPreviousContext() {
        TenantUtil.runAsTenant("t2", () -> assertEquals("t2", TenantUtil.currentTenantCode()));
        assertNull(LoginUserContext.current());
    }

    @Test
    @DisplayName("callAsSystem：跨租户执行并恢复上下文，异常时同样恢复")
    void callAsSystemRestoresContextEvenOnFailure() {
        loginAs("t1", false, false, null);

        Boolean superAdmin = TenantUtil.callAsSystem(() -> LoginUserContext.current().isSuperAdmin());
        assertTrue(superAdmin);
        assertEquals("t1", TenantUtil.currentTenantCode());

        assertThrows(IllegalStateException.class, () ->
                TenantUtil.callAsSystem(() -> {
                    throw new IllegalStateException("boom");
                }));
        assertEquals("t1", TenantUtil.currentTenantCode());
    }
}