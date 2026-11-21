package com.nway.system.metadata.service;

import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.metadata.dao.SysConfigDao;
import com.nway.system.metadata.entity.SysConfig;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 更新路径的租户归属不可变（审计：UPDATE 的 SET 值不受数据层隔离约束）。
 */
@DisplayName("SysConfigService：更新时租户归属不可变")
class SysConfigServiceTenantImmutableTest {

    private final SysConfigDao sysConfigDao = mock(SysConfigDao.class);
    private final TenantNameResolver tenantNameResolver = mock(TenantNameResolver.class);

    private SysConfigService service;

    @BeforeEach
    void setUp() {
        service = new SysConfigService();
        ReflectionTestUtils.setField(service, "sysConfigDao", sysConfigDao);
        ReflectionTestUtils.setField(service, "tenantNameResolver", tenantNameResolver);
        loginAs("t1", false, false, List.of());
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

    private static SysConfig config(String id, String tenantCode) {
        SysConfig config = new SysConfig();
        config.setId(id);
        config.setConfigKey("k1");
        config.setTenantCode(tenantCode);
        return config;
    }

    @Test
    @DisplayName("更新：请求体传的租户被忽略，以库中记录为准")
    void updateKeepsExistingTenant() {
        when(sysConfigDao.queryById("c1")).thenReturn(config("c1", "t1"));
        when(sysConfigDao.countByKey("k1", "c1")).thenReturn(0);

        service.save(config("c1", "public"));

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(sysConfigDao).updateById(captor.capture());
        assertThat(captor.getValue().getTenantCode()).isEqualTo("t1");
    }

    @Test
    @DisplayName("更新：库中记录属公共租户且当前用户无授权时拒绝")
    void updateOnPublicRowDeniedWithoutGrant() {
        when(sysConfigDao.queryById("c2")).thenReturn(config("c2", "public"));

        assertThatThrownBy(() -> service.save(config("c2", "t1")))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("public");
    }

    @Test
    @DisplayName("新增：显式写 public 且无授权时拒绝")
    void insertIntoPublicDeniedWithoutGrant() {
        assertThatThrownBy(() -> service.save(config(null, "public")))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("public");
    }
}
