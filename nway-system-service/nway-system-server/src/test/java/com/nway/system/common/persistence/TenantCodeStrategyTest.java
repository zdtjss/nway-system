package com.nway.system.common.persistence;

import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.spring.jdbc.sql.SqlType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户编码填充策略单元测试：本体写入（严格）与引用写入（关联表）的 public 语义差异。
 */
@DisplayName("TenantCodeStrategy：public 的写入边界")
class TenantCodeStrategyTest {

    private final TenantCodeStrategy strict = new TenantCodeStrategy();
    private final TenantReferenceCodeStrategy reference = new TenantReferenceCodeStrategy();

    @BeforeEach
    void setUp() {
        LoginUserContext.clean();
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    private static void loginAs(String tenantCode, boolean superAdmin, boolean admin, List<String> adminTenants) {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode(tenantCode);
        user.setSuperAdmin(superAdmin);
        user.setAdmin(admin);
        user.setAdminTenantCodes(adminTenants);
        LoginUserContext.set(user);
    }

    @Test
    @DisplayName("本体写入：非超管显式写 public 被拒（读共享≠写授权）")
    void strictStrategyRejectsPublicForTenantUser() {
        loginAs("OA", false, false, List.of());

        assertThatThrownBy(() -> strict.getValue(SqlType.INSERT, "public"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("public");
    }

    @Test
    @DisplayName("本体写入：管理员未被授权 public 时同样被拒")
    void strictStrategyRejectsPublicForUnauthorizedAdmin() {
        loginAs("OA", false, true, List.of("OA"));

        assertThatThrownBy(() -> strict.getValue(SqlType.INSERT, "public"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("public");
    }

    @Test
    @DisplayName("本体写入：管理员显式授权 public 后放行")
    void strictStrategyAllowsPublicWithGrant() {
        loginAs("OA", false, true, List.of("OA", "public"));

        assertThat(strict.getValue(SqlType.INSERT, "public")).isEqualTo("public");
    }

    @Test
    @DisplayName("本体写入：超管不受限")
    void strictStrategyAllowsPublicForSuperAdmin() {
        loginAs("default", true, true, List.of());

        assertThat(strict.getValue(SqlType.INSERT, "public")).isEqualTo("public");
    }

    @Test
    @DisplayName("本体写入：本租户显式写入放行")
    void strictStrategyAllowsOwnTenant() {
        loginAs("OA", false, false, List.of());

        assertThat(strict.getValue(SqlType.INSERT, "OA")).isEqualTo("OA");
    }

    @Test
    @DisplayName("引用写入：关联表可引用 public 资源（如绑定公共角色）")
    void referenceStrategyAllowsPublicForTenantUser() {
        loginAs("OA", false, false, List.of());

        assertThat(reference.getValue(SqlType.INSERT, "public")).isEqualTo("public");
    }

    @Test
    @DisplayName("引用写入：引用其他租户仍被拒")
    void referenceStrategyStillRejectsOtherTenant() {
        loginAs("OA", false, false, List.of());

        assertThatThrownBy(() -> reference.getValue(SqlType.INSERT, "default"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("default");
    }

    @Test
    @DisplayName("无租户上下文且未开启回落：fail-closed 拒绝")
    void failClosedWithoutContext() {
        assertThatThrownBy(() -> strict.getValue(SqlType.INSERT, null))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("fail-closed");
    }
}
