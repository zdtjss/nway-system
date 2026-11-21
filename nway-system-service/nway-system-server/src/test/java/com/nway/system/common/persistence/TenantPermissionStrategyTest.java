package com.nway.system.common.persistence;

import com.nway.spring.jdbc.sql.SqlType;
import com.nway.spring.jdbc.sql.permission.WhereCondition;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 租户自动隔离策略单元测试。
 * <p>
 * 语义基线（与 {@link TenantUtil#currentTenantCodesForIsolation()} 保持一致）：
 * <ul>
 *   <li>INSERT：不追加条件（由 fillStrategy 填充）</li>
 *   <li>超管 / 显式系统上下文（callAsSystem）：不隔离</li>
 *   <li>无登录上下文：fail-closed（恒假条件 1 = 0，不返回任何行）</li>
 *   <li>普通用户 SELECT：tenant_code IN (本租户, public)</li>
 *   <li>平台管理员 SELECT：tenant_code IN (本租户, 授权租户..., public)</li>
 *   <li>管理员指定工作租户 SELECT：tenant_code IN (工作租户, public)</li>
 *   <li>UPDATE/DELETE（写边界）：用可管理集合，<b>不含 public</b>（读共享 ≠ 写授权）</li>
 * </ul>
 */
class TenantPermissionStrategyTest {

    private final TenantPermissionStrategy strategy = new TenantPermissionStrategy();

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
    @DisplayName("INSERT 操作不追加租户条件")
    void insertShouldReturnNull() {
        loginAs("tenant001", false, false, null);

        WhereCondition result = strategy.getSqlSegment(SqlType.INSERT, "tenant_code");
        assertNull(result, "INSERT 操作不应追加租户条件");
    }

    @Test
    @DisplayName("超级管理员不追加租户条件")
    void superAdminShouldReturnNull() {
        loginAs("tenant001", true, false, null);

        WhereCondition result = strategy.getSqlSegment(SqlType.SELECT, "tenant_code");
        assertNull(result, "超级管理员不应追加租户条件");
    }

    @Test
    @DisplayName("无登录上下文 fail-closed：追加恒假条件 1 = 0（不返回任何行）")
    void noContextShouldFailClosed() {
        WhereCondition result = strategy.getSqlSegment(SqlType.SELECT, "tenant_code");
        assertNotNull(result, "无登录上下文应 fail-closed，而非全域放行");
        assertEquals("1 = ?", result.getExpr());
        assertEquals(0, result.getValue());
    }

    @Test
    @DisplayName("普通用户 SELECT 追加 IN (本租户, public)")
    void normalUserSelectShouldIsolateToOwnTenantAndPublic() {
        loginAs("tenant001", false, false, null);

        WhereCondition result = strategy.getSqlSegment(SqlType.SELECT, "tenant_code");
        assertNotNull(result, "普通用户 SELECT 应追加租户条件");
        assertEquals("tenant_code IN (?, ?)", result.getExpr());
        assertEquals(List.of("tenant001", TenantUtil.PUBLIC_TENANT_CODE), result.getValue());
    }

    @Test
    @DisplayName("普通用户 UPDATE 收敛到本租户（写边界排除 public）")
    void normalUserUpdateShouldAddCondition() {
        loginAs("tenant002", false, false, null);

        WhereCondition result = strategy.getSqlSegment(SqlType.UPDATE, "tenant_code");
        assertNotNull(result, "普通用户 UPDATE 应追加租户条件");
        assertEquals("tenant_code = ?", result.getExpr());
        assertEquals("tenant002", result.getValue());
    }

    @Test
    @DisplayName("普通用户 DELETE 收敛到本租户（写边界排除 public）")
    void normalUserDeleteShouldAddCondition() {
        loginAs("tenant003", false, false, null);

        WhereCondition result = strategy.getSqlSegment(SqlType.DELETE, "tenant_code");
        assertNotNull(result, "普通用户 DELETE 应追加租户条件");
        assertEquals("tenant_code = ?", result.getExpr());
        assertEquals("tenant003", result.getValue());
    }

    @Test
    @DisplayName("管理员 UPDATE 使用可管理集合，不含 public（读共享 ≠ 写授权）")
    void adminUpdateExcludesPublic() {
        loginAs("tenant001", false, true, List.of("tenant002"));

        WhereCondition result = strategy.getSqlSegment(SqlType.UPDATE, "tenant_code");
        assertNotNull(result, "管理员 UPDATE 应追加租户条件");
        assertEquals("tenant_code IN (?, ?)", result.getExpr());
        assertEquals(List.of("tenant001", "tenant002"), result.getValue());
    }

    @Test
    @DisplayName("非超管且租户编码为 null 时仍可见 public（不绕过隔离）")
    void nullTenantCodeShouldIsolateToPublicOnly() {
        loginAs(null, false, false, null);

        WhereCondition result = strategy.getSqlSegment(SqlType.SELECT, "tenant_code");
        assertNotNull(result, "租户编码为 null 时不应绕过隔离");
        assertEquals("tenant_code = ?", result.getExpr());
        assertEquals(TenantUtil.PUBLIC_TENANT_CODE, result.getValue());
    }

    @Test
    @DisplayName("管理员带授权租户时生成 IN（本租户 + 授权租户 + public）")
    void adminWithAuthorizedTenantsShouldAddInCondition() {
        loginAs("tenant001", false, true, List.of("tenant002", "tenant003"));

        WhereCondition result = strategy.getSqlSegment(SqlType.SELECT, "tenant_code");
        assertNotNull(result, "管理员应追加租户条件");
        assertEquals("tenant_code IN (?, ?, ?, ?)", result.getExpr());
        assertEquals(List.of("tenant001", "tenant002", "tenant003", TenantUtil.PUBLIC_TENANT_CODE),
                result.getValue());
    }

    @Test
    @DisplayName("管理员授权租户与自身租户重复时不产生重复值")
    void adminDuplicateTenantShouldBeDeduplicated() {
        loginAs("tenant001", false, true, List.of("tenant001", "tenant002"));

        WhereCondition result = strategy.getSqlSegment(SqlType.SELECT, "tenant_code");
        assertNotNull(result);
        assertEquals("tenant_code IN (?, ?, ?)", result.getExpr());
        assertEquals(List.of("tenant001", "tenant002", TenantUtil.PUBLIC_TENANT_CODE), result.getValue());
    }

    @Test
    @DisplayName("管理员指定工作租户时，隔离收敛到该工作租户（+ public）")
    void adminWithWorkTenantShouldNarrowIsolation() {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode("tenant001");
        user.setAdmin(true);
        user.setAdminTenantCodes(List.of("tenant002", "tenant003"));
        user.setCurrentWorkTenantCode("tenant003");
        LoginUserContext.set(user);

        WhereCondition result = strategy.getSqlSegment(SqlType.SELECT, "tenant_code");
        assertNotNull(result);
        assertEquals("tenant_code IN (?, ?)", result.getExpr());
        assertEquals(List.of("tenant003", TenantUtil.PUBLIC_TENANT_CODE), result.getValue());
    }

    @Test
    @DisplayName("非管理员携带工作租户不影响隔离（仅管理员生效）")
    void nonAdminWorkTenantShouldBeIgnored() {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode("tenant001");
        user.setCurrentWorkTenantCode("tenant009");
        LoginUserContext.set(user);

        WhereCondition result = strategy.getSqlSegment(SqlType.SELECT, "tenant_code");
        assertNotNull(result);
        assertEquals(List.of("tenant001", TenantUtil.PUBLIC_TENANT_CODE), result.getValue());
    }
}