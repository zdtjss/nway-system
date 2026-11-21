package com.nway.system.openplatform.web.open;

import com.nway.system.openplatform.web.model.OpenResourceCatalogVo;
import com.nway.system.common.web.Result;
import com.nway.system.openplatform.web.context.OpenAppContext;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.portal.entity.Menu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 开放平台资源目录测试（权限模型重构 S1-3 / 契约 C-CATALOG）。
 * <p>
 * 目录是"权限标识符可发现"的落点，也是 wform 启动自检的数据来源
 * （见 权限模型重构技术方案 §4.2.4），故重点锁住：资源/动作的归集口径、禁用菜单的过滤、
 * 上下文缺失时的 fail-closed。
 */
@DisplayName("OpenMetaController：应用资源目录")
class OpenMetaControllerTest {

    private final MenuDao menuDao = mock(MenuDao.class);

    private OpenMetaController controller;

    @BeforeEach
    void setUp() {
        controller = new OpenMetaController();
        ReflectionTestUtils.setField(controller, "menuDao", menuDao);
    }

    @AfterEach
    void tearDown() {
        // 避免 ThreadLocal 残留影响其它测试
        OpenAppContext.clear();
    }

    /** 构造菜单；parentId 为空串表示根。 */
    private static Menu menu(String id, String parentId, String name, String type,
                             String resourceCode, String action) {
        Menu m = new Menu();
        m.setId(id);
        m.setParentId(parentId);
        m.setName(name);
        m.setType(type);
        m.setResourceCode(resourceCode);
        m.setAction(action);
        m.setAppCode("wform");
        m.setIsEnabled(true);
        return m;
    }

    private void asApp(String appCode, String tenantCode) {
        OpenAppContext.set(new OpenAppContext.OpenAppInfo(
                appCode, tenantCode, List.of(), "ALL", List.of(), null));
    }

    @Test
    @DisplayName("能力资源：PAGE 为资源，其下 BTN 为动作")
    void buildsResourcesAndActions() {
        asApp("wform", "OA");
        when(menuDao.queryByAppInTenants(eq("wform"), any())).thenReturn(List.of(
                menu("d1", "", "低代码能力", "DIR", null, null),
                menu("p1", "d1", "表单设计器", "PAGE", "wform:designer", null),
                menu("b1", "p1", "查看表单", "BTN", "wform:designer", "read"),
                menu("b2", "p1", "设计与保存", "BTN", "wform:designer", "design")));

        Result<OpenResourceCatalogVo> result = controller.catalog();

        assertThat(result.getData().getAppCode()).isEqualTo("wform");
        assertThat(result.getData().getResources()).hasSize(1);
        OpenResourceCatalogVo.Resource res = result.getData().getResources().get(0);
        assertThat(res.getResourceCode()).isEqualTo("wform:designer");
        assertThat(res.getName()).isEqualTo("表单设计器");
        assertThat(res.getActions()).extracting(OpenResourceCatalogVo.Action::getAction)
                .containsExactly("read", "design");
    }

    @Test
    @DisplayName("BTN 与 PAGE 之间隔 DIR 层：仍能归集到正确的 PAGE")
    void resolvesActionAcrossDirLevel() {
        asApp("wform", "OA");
        when(menuDao.queryByAppInTenants(eq("wform"), any())).thenReturn(List.of(
                menu("p1", "", "表单数据", "PAGE", "wform:data", null),
                menu("mid", "p1", "分组", "DIR", null, null),
                menu("b1", "mid", "写入", "BTN", "wform:data", "write")));

        Result<OpenResourceCatalogVo> result = controller.catalog();

        assertThat(result.getData().getResources()).hasSize(1);
        assertThat(result.getData().getResources().get(0).getActions())
                .extracting(OpenResourceCatalogVo.Action::getAction)
                .containsExactly("write");
    }

    @Test
    @DisplayName("同名动作重复：动作列表去重")
    void deduplicatesActions() {
        asApp("wform", "OA");
        when(menuDao.queryByAppInTenants(eq("wform"), any())).thenReturn(List.of(
                menu("p1", "", "表单设计器", "PAGE", "wform:designer", null),
                menu("b1", "p1", "查看表单", "BTN", "wform:designer", "read"),
                menu("b2", "p1", "查看表单2", "BTN", "wform:designer", "read")));

        Result<OpenResourceCatalogVo> result = controller.catalog();

        assertThat(result.getData().getResources().get(0).getActions()).hasSize(1);
    }

    @Test
    @DisplayName("禁用菜单不进目录")
    void excludesDisabledMenus() {
        asApp("wform", "OA");
        Menu disabled = menu("p2", "", "已停用页面", "PAGE", "wform:legacy", null);
        disabled.setIsEnabled(false);
        when(menuDao.queryByAppInTenants(eq("wform"), any())).thenReturn(List.of(
                menu("p1", "", "表单设计器", "PAGE", "wform:designer", null),
                disabled));

        Result<OpenResourceCatalogVo> result = controller.catalog();

        assertThat(result.getData().getResources())
                .extracting(OpenResourceCatalogVo.Resource::getResourceCode)
                .containsExactly("wform:designer");
    }

    @Test
    @DisplayName("无 APP 上下文：返回空目录（fail-closed，不外泄全量资源）")
    void withoutContextReturnsEmpty() {
        OpenAppContext.clear();

        Result<OpenResourceCatalogVo> result = controller.catalog();

        assertThat(result.getData().getResources()).isEmpty();
        assertThat(result.getData().getAppCode()).isNull();
    }
}