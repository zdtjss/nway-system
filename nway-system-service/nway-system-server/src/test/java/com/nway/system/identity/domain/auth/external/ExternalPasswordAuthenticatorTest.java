package com.nway.system.identity.domain.auth.external;

import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外部密码校验器：开关校验、外部用户映射、改密委托。
 */
@DisplayName("ExternalPasswordAuthenticator：外部凭据校验")
class ExternalPasswordAuthenticatorTest {

    private ExternalAuthProperties properties;
    private ExternalAuthClient externalAuthClient;
    private ExternalUserProvisioner externalUserProvisioner;
    private ExternalPasswordAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        properties = new ExternalAuthProperties();
        externalAuthClient = mock(ExternalAuthClient.class);
        externalUserProvisioner = mock(ExternalUserProvisioner.class);
        authenticator = new ExternalPasswordAuthenticator();
        ReflectionTestUtils.setField(authenticator, "properties", properties);
        ReflectionTestUtils.setField(authenticator, "externalAuthClient", externalAuthClient);
        ReflectionTestUtils.setField(authenticator, "externalUserProvisioner", externalUserProvisioner);
    }

    @Test
    @DisplayName("未启用：拒绝认证且不调用外部服务")
    void rejectsWhenDisabled() {
        assertThatThrownBy(() -> authenticator.authenticate(loginDto()))
                .isInstanceOf(NwaySystemException.class)
                .satisfies(e -> assertThat(((NwaySystemException) e).getCode())
                        .isEqualTo(ExternalAuthClient.NOT_CONFIGURED));
        verify(externalAuthClient, never()).authenticate(anyString(), anyString());
    }

    @Test
    @DisplayName("外部校验通过：交由映射器落地平台登录用户")
    void delegatesProvisioning() {
        properties.setEnabled(true);
        ExternalAuthClient.ExternalUser externalUser = new ExternalAuthClient.ExternalUser();
        externalUser.setEmployeeId("10086");
        LoginUser expected = new LoginUser();
        expected.setEmployeeId("10086");
        when(externalAuthClient.authenticate("10086", "pwd")).thenReturn(externalUser);
        when(externalUserProvisioner.resolve(externalUser, "10086")).thenReturn(expected);

        assertThat(authenticator.authenticate(loginDto())).isSameAs(expected);
    }

    @Test
    @DisplayName("自助改密：委托外部系统校验旧密码")
    void delegatesChangePassword() {
        authenticator.changePassword("10086", "old", "new");

        verify(externalAuthClient).changePassword("10086", "old", "new");
        verify(externalUserProvisioner, never()).resolve(any(), anyString());
    }

    private static LoginDto loginDto() {
        LoginDto dto = new LoginDto();
        dto.setName("10086");
        dto.setKey("pwd");
        dto.setLoginType("password");
        return dto;
    }
}
