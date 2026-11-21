package com.nway.system.identity.domain.auth.external;

import com.nway.system.identity.service.LoginUserAssembler;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.identity.domain.enums.UserStatusEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外部身份映射：租户校验、影子账号落地、禁用校验。
 */
@DisplayName("ExternalUserProvisioner：外部身份映射")
class ExternalUserProvisionerTest {

    private ExternalAuthProperties properties;
    private UserDao userDao;
    private TenantDao tenantDao;
    private LoginUserAssembler loginUserAssembler;
    private ExternalUserProvisioner provisioner;

    @BeforeEach
    void setUp() {
        properties = new ExternalAuthProperties();
        properties.setTenantCode("nway");
        userDao = mock(UserDao.class);
        tenantDao = mock(TenantDao.class);
        loginUserAssembler = mock(LoginUserAssembler.class);
        provisioner = new ExternalUserProvisioner();
        ReflectionTestUtils.setField(provisioner, "properties", properties);
        ReflectionTestUtils.setField(provisioner, "userDao", userDao);
        ReflectionTestUtils.setField(provisioner, "tenantDao", tenantDao);
        ReflectionTestUtils.setField(provisioner, "loginUserAssembler", loginUserAssembler);
    }

    @Test
    @DisplayName("本地已有用户：直接装配登录用户，不重复建号")
    void usesExistingLocalUser() {
        when(tenantDao.getByCode("nway")).thenReturn(new Tenant());
        when(userDao.getByEmployeeId("10086")).thenReturn(new User());
        LoginUser expected = new LoginUser();
        expected.setEmployeeId("10086");
        when(loginUserAssembler.getByEmployeeId("10086")).thenReturn(expected);

        assertThat(provisioner.resolve(externalUser(), "10086")).isSameAs(expected);
        verify(userDao, never()).insert(any());
    }

    @Test
    @DisplayName("本地无用户且允许自动开通：落地影子账号")
    void provisionsShadowUser() {
        when(tenantDao.getByCode("nway")).thenReturn(new Tenant());
        when(userDao.getByEmployeeId("10086")).thenReturn(null);
        LoginUser expected = new LoginUser();
        expected.setEmployeeId("10086");
        when(loginUserAssembler.getByEmployeeId("10086")).thenReturn(expected);

        assertThat(provisioner.resolve(externalUser(), "10086")).isSameAs(expected);
        verify(userDao).insert(any(User.class));
    }

    @Test
    @DisplayName("本地无用户且禁止自动开通：提示账号未开通")
    void rejectsWhenProvisioningDisabled() {
        properties.setAutoProvision(false);
        when(tenantDao.getByCode("nway")).thenReturn(new Tenant());
        when(userDao.getByEmployeeId("10086")).thenReturn(null);

        assertThatThrownBy(() -> provisioner.resolve(externalUser(), "10086"))
                .isInstanceOf(NwaySystemException.class)
                .satisfies(e -> assertThat(((NwaySystemException) e).getCode())
                        .isEqualTo(ExternalAuthClient.USER_NOT_FOUND));
        verify(userDao, never()).insert(any());
    }

    @Test
    @DisplayName("外部用户归属租户未初始化：拒绝登录并提示")
    void rejectsWhenTenantMissing() {
        when(tenantDao.getByCode("nway")).thenReturn(null);

        assertThatThrownBy(() -> provisioner.resolve(externalUser(), "10086"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("租户未初始化");
    }

    @Test
    @DisplayName("本地账号已禁用：拒绝登录")
    void rejectsDisabledUser() {
        when(tenantDao.getByCode("nway")).thenReturn(new Tenant());
        User disabled = new User();
        disabled.setStatus(UserStatusEnum.DISABLED.getValue());
        when(userDao.getByEmployeeId("10086")).thenReturn(disabled);

        assertThatThrownBy(() -> provisioner.resolve(externalUser(), "10086"))
                .isInstanceOf(NwaySystemException.class)
                .satisfies(e -> assertThat(((NwaySystemException) e).getCode())
                        .isEqualTo(ErrorCodeEnum.ACCOUNT_LOCKED.getCode()));
    }

    @Test
    @DisplayName("外部系统未返回工号：拒绝登录")
    void rejectsWhenEmployeeIdMissing() {
        assertThatThrownBy(() -> provisioner.resolve(new ExternalAuthClient.ExternalUser(), null))
                .isInstanceOf(NwaySystemException.class)
                .satisfies(e -> assertThat(((NwaySystemException) e).getCode())
                        .isEqualTo(ExternalAuthClient.USER_NOT_FOUND));
    }

    private static ExternalAuthClient.ExternalUser externalUser() {
        ExternalAuthClient.ExternalUser user = new ExternalAuthClient.ExternalUser();
        user.setEmployeeId("10086");
        user.setRealName("张三");
        return user;
    }
}
