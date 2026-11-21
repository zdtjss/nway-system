package com.nway.system.identity.service;

import com.nway.system.portal.service.MenuService;
import com.nway.system.authorization.service.resource.ResourceService;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.portal.entity.Menu;
import com.nway.system.authorization.web.model.ResourceVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 策略配置弹窗的授权候选读取范围单元测试。
 * <p>
 * 背景：弹窗原复用 menu/queryByApp 与 resource/list（menu 权限），租户管理员无菜单管理权限时
 * 403 导致菜单树为空、存量策略无法回显；改走 role 权限接口后必须仍守住租户边界。
 */
@DisplayName("RoleService：授权候选读取范围")
class RoleServiceGrantableScopeTest {

    private final MenuService menuService = mock(MenuService.class);
    private final ResourceService resourceService = mock(ResourceService.class);

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService();
        ReflectionTestUtils.setField(service, "menuService", menuService);
        ReflectionTestUtils.setField(service, "resourceService", resourceService);
        LoginUserContext.clean();
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    private static void login(String tenantCode, boolean superAdmin) {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode(tenantCode);
        user.setSuperAdmin(superAdmin);
        LoginUserContext.set(user);
    }

    @Test
    @DisplayName("普通用户读取他租户菜单：拒绝且不触达菜单查询")
    void rejectsOtherTenantMenu() {
        login("A", false);

        assertThatThrownBy(() -> service.queryGrantableMenus("app", "B"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("无权限读取该租户的菜单");
        verify(menuService, never()).queryByApp("app", "B");
    }

    @Test
    @DisplayName("普通用户读取本租户菜单：放行")
    void allowsOwnTenantMenu() {
        login("A", false);
        Menu menu = new Menu();
        menu.setId("m1");
        when(menuService.queryByApp("app", "A")).thenReturn(List.of(menu));

        assertThat(service.queryGrantableMenus("app", "A")).extracting(Menu::getId).containsExactly("m1");
    }

    @Test
    @DisplayName("超管：不限租户")
    void superAdminNotLimited() {
        login("default", true);
        when(menuService.queryByApp("app", "B")).thenReturn(List.of());

        assertThat(service.queryGrantableMenus("app", "B")).isEmpty();
    }

    @Test
    @DisplayName("资源动作目录：委托资源目录的可见性规则")
    void delegatesResourceActions() {
        login("A", false);
        ResourceVo vo = new ResourceVo("r1", "app", "x:list", "PAGE", "列表",
                null, "TENANT", "A", "ACTIVE", List.of());
        when(resourceService.list("app", null)).thenReturn(List.of(vo));

        assertThat(service.queryGrantableResources("app")).extracting(ResourceVo::resourceCode)
                .containsExactly("x:list");
    }
}
