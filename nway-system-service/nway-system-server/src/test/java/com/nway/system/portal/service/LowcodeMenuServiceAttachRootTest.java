package com.nway.system.portal.service;

import com.nway.system.portal.web.model.LowcodeMenuSyncItem;
import com.nway.system.portal.web.model.LowcodeMenuSyncRequest;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.openplatform.web.context.OpenAppContext;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.portal.entity.Menu;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.authorization.service.policy.PolicyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 低代码菜单同步：attachToAppRoot（显式挂应用根）语义。
 */
@DisplayName("LowcodeMenuService：attachToAppRoot 挂应用根")
class LowcodeMenuServiceAttachRootTest {

    private static final String TENANT = "OA";
    private static final String APP = "sys_oa";

    private final MenuDao menuDao = mock(MenuDao.class);
    private final AppDao appDao = mock(AppDao.class);
    private final RoleDao roleDao = mock(RoleDao.class);
    private final PolicyService policyService = mock(PolicyService.class);

    private LowcodeMenuService service;

    @BeforeEach
    void setUp() {
        service = new LowcodeMenuService();
        ReflectionTestUtils.setField(service, "menuDao", menuDao);
        ReflectionTestUtils.setField(service, "appDao", appDao);
        ReflectionTestUtils.setField(service, "roleDao", roleDao);
        ReflectionTestUtils.setField(service, "policyService", policyService);

        OpenAppContext.set(new OpenAppContext.OpenAppInfo(
                "wform", TENANT, List.of(), "ALL", List.of(), "E1"));
        LoginUser user = new LoginUser();
        user.setEmployeeId("E1");
        user.setTenantCode(TENANT);
        LoginUserContext.set(user);

        App app = new App();
        app.setAppCode(APP);
        app.setTenantCode(TENANT);
        app.setStatus("using");
        when(appDao.getByCode(APP)).thenReturn(app);
    }

    @AfterEach
    void tearDown() {
        OpenAppContext.clear();
        LoginUserContext.clean();
    }

    @Test
    @DisplayName("更新已有菜单时显式挂应用根：原父级被改写为空串")
    void movesExistingMenuToAppRoot() {
        Menu existing = new Menu();
        existing.setId("m1");
        existing.setParentId("old-parent");
        existing.setExternalId("wform-page-2");
        existing.setAppCode(APP);
        existing.setTenantCode(TENANT);
        existing.setType("PAGE");
        when(menuDao.queryByAppAndExternalIds(eq(APP), any())).thenReturn(List.of(existing));

        service.sync(request(item("wform-page-2", true)));

        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuDao).update(captor.capture());
        verify(menuDao, never()).insert(any());
        assertThat(captor.getValue().getParentId()).isEmpty();
    }

    @Test
    @DisplayName("attachToAppRoot 与父级引用互斥：整批拒绝")
    void rejectsAttachWithParentReference() {
        LowcodeMenuSyncItem item = item("wform-page-2", true);
        item.setExternalParentId("wform-dir-1");

        assertThatThrownBy(() -> service.sync(request(item)))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("互斥");
        verify(menuDao, never()).update(any());
    }

    private static LowcodeMenuSyncItem item(String externalId, boolean attachToAppRoot) {
        LowcodeMenuSyncItem item = new LowcodeMenuSyncItem();
        item.setExternalId(externalId);
        item.setType("PAGE");
        item.setName("订单列表");
        item.setUrl("/data/order");
        item.setResourceCode("order:list");
        item.setAttachToAppRoot(attachToAppRoot);
        return item;
    }

    private static LowcodeMenuSyncRequest request(LowcodeMenuSyncItem... items) {
        LowcodeMenuSyncRequest request = new LowcodeMenuSyncRequest();
        request.setAppCode(APP);
        request.setItems(List.of(items));
        return request;
    }
}
