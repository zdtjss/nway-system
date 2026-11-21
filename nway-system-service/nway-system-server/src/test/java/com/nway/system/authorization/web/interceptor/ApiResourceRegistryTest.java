package com.nway.system.authorization.web.interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务端 API → 资源权限映射测试（接口授权 fail-closed 的核心）。
 */
class ApiResourceRegistryTest {

    @Test
    @DisplayName("已登记接口按首段路径解析资源标识")
    void registeredPathsShouldResolveResource() {
        assertEquals("user", ApiResourceRegistry.resolve("/user/save").resourceCode());
        assertEquals("user", ApiResourceRegistry.resolve("/userDept/tree").resourceCode());
        assertEquals("role", ApiResourceRegistry.resolve("/userRole/queryByUser").resourceCode());
        assertEquals("role", ApiResourceRegistry.resolve("/policy/saveRolePolicies").resourceCode());
        assertEquals("dept", ApiResourceRegistry.resolve("/dept/treeByTenantCode").resourceCode());
        assertEquals("online_user", ApiResourceRegistry.resolve("/onlineUser/kick").resourceCode());
        assertEquals(ApiResourceRegistry.PLATFORM_APP_CODE,
                ApiResourceRegistry.resolve("/user/save").appCode());
    }

    @Test
    @DisplayName("未登记路径解析为 null（调用方必须拒绝）")
    void unregisteredPathsShouldReturnNull() {
        assertNull(ApiResourceRegistry.resolve("/unknown/doSomething"));
        assertNull(ApiResourceRegistry.resolve("/open/user/getByEmployeeId"));
        assertNull(ApiResourceRegistry.resolve("/"));
        assertNull(ApiResourceRegistry.resolve(""));
        assertNull(ApiResourceRegistry.resolve(null));
    }

    @Test
    @DisplayName("删除类接口仅需 delete 动作")
    void deleteActions() {
        assertEquals(List.of("delete"), ApiResourceRegistry.resolveActions("/dict/deleteType", "POST"));
        assertEquals(List.of("delete"), ApiResourceRegistry.resolveActions("/user/removeUser", "POST"));
    }

    @Test
    @DisplayName("保存类接口同时接受 create 与 edit（save 兼具新增与修改语义）")
    void saveActions() {
        assertEquals(List.of("create", "edit"), ApiResourceRegistry.resolveActions("/user/save", "POST"));
        assertEquals(List.of("create", "edit"), ApiResourceRegistry.resolveActions("/dict/saveItem", "POST"));
    }

    @Test
    @DisplayName("修改类接口仅需 edit 动作")
    void editActions() {
        assertEquals(List.of("edit"), ApiResourceRegistry.resolveActions("/user/enable", "POST"));
        assertEquals(List.of("edit"), ApiResourceRegistry.resolveActions("/user/disable", "POST"));
    }

    @Test
    @DisplayName("高危接口显式声明动作，优先于词根/方法推断")
    void explicitActionsForHighRiskEndpoints() {
        // 敏感操作单独声明为 reset，不被泛化的 edit 顺带放行
        assertEquals(List.of("reset"), ApiResourceRegistry.resolveActions("/user/resetPassword", "POST"));
        assertEquals(List.of("reset"), ApiResourceRegistry.resolveActions("/openApp/resetSecret", "POST"));
        // 名字不含动作词根的修改类操作：显式 edit，避免被 POST 兜底的 create 放行
        assertEquals(List.of("edit"), ApiResourceRegistry.resolveActions("/onlineUser/forceLogout", "POST"));
        assertEquals(List.of("edit"), ApiResourceRegistry.resolveActions("/onlineUser/forceLogoutSession", "POST"));
        assertEquals(List.of("edit"), ApiResourceRegistry.resolveActions("/openApp/toggleStatus", "POST"));
        // 角色复制是新增行为：显式 create
        assertEquals(List.of("create"), ApiResourceRegistry.resolveActions("/role/copyToTenant", "POST"));
        assertEquals(List.of("create"), ApiResourceRegistry.resolveActions("/admin/grant", "POST"));
        assertEquals(List.of("delete"), ApiResourceRegistry.resolveActions("/admin/revoke", "POST"));
        // 尾斜杠归一化后仍命中显式声明
        assertEquals(List.of("reset"), ApiResourceRegistry.resolveActions("/user/resetPassword/", "POST"));
    }

    @Test
    @DisplayName("查询类接口仅需 view 动作")
    void viewActions() {
        assertEquals(List.of("view"), ApiResourceRegistry.resolveActions("/user/queryPage", "GET"));
        assertEquals(List.of("view"), ApiResourceRegistry.resolveActions("/dept/tree", "GET"));
        assertEquals(List.of("view"), ApiResourceRegistry.resolveActions("/message/polling", "GET"));
    }

    @Test
    @DisplayName("未知动作词根回退按 HTTP 方法推导")
    void fallbackByHttpMethod() {
        assertEquals(List.of("view"), ApiResourceRegistry.resolveActions("/user/whatever", "GET"));
        assertEquals(List.of("create", "edit"), ApiResourceRegistry.resolveActions("/user/whatever", "POST"));
        assertEquals(List.of("edit"), ApiResourceRegistry.resolveActions("/user/whatever", "PUT"));
        assertEquals(List.of("delete"), ApiResourceRegistry.resolveActions("/user/whatever", "DELETE"));
        assertTrue(ApiResourceRegistry.resolveActions("/user/whatever", "TRACE").isEmpty());
    }
}