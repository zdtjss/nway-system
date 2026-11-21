package com.nway.system.portal.web.admin;

import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.MenuDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 应用保存的租户护栏（审计 P0-3）：公共共享应用租户不可变更；私有应用改租户仅同步平台菜单。
 */
@DisplayName("AppController：应用租户护栏")
class AppControllerTenantGuardTest {

    private final AppDao appDao = mock(AppDao.class);
    private final MenuDao menuDao = mock(MenuDao.class);
    private final TenantNameResolver tenantNameResolver = mock(TenantNameResolver.class);

    private AppController controller;

    @BeforeEach
    void setUp() {
        controller = new AppController();
        ReflectionTestUtils.setField(controller, "appDao", appDao);
        ReflectionTestUtils.setField(controller, "menuDao", menuDao);
        ReflectionTestUtils.setField(controller, "tenantNameResolver", tenantNameResolver);
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

    private static App app(String id, String appCode, String tenantCode) {
        App app = new App();
        app.setId(id);
        app.setAppCode(appCode);
        app.setAppName(appCode);
        app.setTenantCode(tenantCode);
        return app;
    }

    @Test
    @DisplayName("公共共享应用：变更归属租户被拒")
    void publicAppTenantChangeRejected() {
        when(appDao.getById("a1")).thenReturn(app("a1", "wform", "public"));

        assertThatThrownBy(() -> controller.save(app("a1", "wform", "OA")))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("不可变更");
        verify(menuDao, never()).updateTenantByApp("wform", "OA");
    }

    @Test
    @DisplayName("私有应用：改租户放行，并级联同步平台菜单")
    void privateAppTenantChangeCascades() {
        when(appDao.getById("a2")).thenReturn(app("a2", "sys_oa", "OA"));

        controller.save(app("a2", "sys_oa", "default"));

        verify(menuDao).updateTenantByApp("sys_oa", "default");
        ArgumentCaptor<App> captor = ArgumentCaptor.forClass(App.class);
        verify(appDao).updateById(captor.capture());
        assertThat(captor.getValue().getTenantCode()).isEqualTo("default");
    }

    @Test
    @DisplayName("非超管：新增应用的租户被强制为本租户")
    void nonSuperAdminTenantForcedToOwn() {
        loginAs("OA", false);

        controller.save(app(null, "oa_new", "default"));

        ArgumentCaptor<App> captor = ArgumentCaptor.forClass(App.class);
        verify(appDao).insert(captor.capture());
        assertThat(captor.getValue().getTenantCode()).isEqualTo("OA");
    }
}
