package com.nway.system.identity.domain.auth.impl;

import com.nway.system.identity.domain.auth.external.ExternalAuthClient;
import com.nway.system.identity.domain.auth.external.ExternalAuthProperties;
import com.nway.system.identity.domain.auth.external.ExternalPasswordAuthenticator;
import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.identity.domain.enums.LoginTypeEnum;
import com.nway.system.common.exception.NwaySystemException;
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
 * 账号密码登录：身份源（本地 / 外部）路由与回退策略。
 */
@DisplayName("PasswordAuthorization：密码身份源路由")
class PasswordAuthorizationTest {

    private ExternalAuthProperties properties;
    private ExternalPasswordAuthenticator externalAuthenticator;
    private LocalPasswordAuthenticator localAuthenticator;
    private PasswordAuthorization authorization;

    @BeforeEach
    void setUp() {
        properties = new ExternalAuthProperties();
        externalAuthenticator = mock(ExternalPasswordAuthenticator.class);
        localAuthenticator = mock(LocalPasswordAuthenticator.class);
        authorization = new PasswordAuthorization();
        ReflectionTestUtils.setField(authorization, "externalAuthProperties", properties);
        ReflectionTestUtils.setField(authorization, "externalPasswordAuthenticator", externalAuthenticator);
        ReflectionTestUtils.setField(authorization, "localPasswordAuthenticator", localAuthenticator);
    }

    @Test
    @DisplayName("外部认证关闭：走平台本地密码校验")
    void usesLocalWhenExternalDisabled() {
        LoginUser local = loginUser("admin");
        when(localAuthenticator.authenticate(any())).thenReturn(local);

        assertThat(authorization.authenticate(loginDto())).isSameAs(local);
        verify(externalAuthenticator, never()).authenticate(any());
    }

    @Test
    @DisplayName("外部认证开启：优先外部身份系统校验")
    void usesExternalWhenEnabled() {
        properties.setEnabled(true);
        LoginUser external = loginUser("10086");
        when(externalAuthenticator.authenticate(any())).thenReturn(external);

        assertThat(authorization.authenticate(loginDto())).isSameAs(external);
        verify(localAuthenticator, never()).authenticate(any());
    }

    @Test
    @DisplayName("外部系统判定账号不存在且允许回退：转本地校验（保留平台管理员入口）")
    void fallsBackToLocalWhenAccountMissing() {
        properties.setEnabled(true);
        when(externalAuthenticator.authenticate(any()))
                .thenThrow(new NwaySystemException(ExternalAuthClient.ACCOUNT_NOT_FOUND, "AD 中不存在该工号"));
        LoginUser local = loginUser("admin");
        when(localAuthenticator.authenticate(any())).thenReturn(local);

        assertThat(authorization.authenticate(loginDto())).isSameAs(local);
    }

    @Test
    @DisplayName("关闭本地回退：外部账号不存在直接失败")
    void doesNotFallbackWhenDisabled() {
        properties.setEnabled(true);
        properties.setLocalFallback(false);
        when(externalAuthenticator.authenticate(any()))
                .thenThrow(new NwaySystemException(ExternalAuthClient.ACCOUNT_NOT_FOUND, "AD 中不存在该工号"));

        assertThatThrownBy(() -> authorization.authenticate(loginDto()))
                .isInstanceOf(NwaySystemException.class)
                .satisfies(e -> assertThat(((NwaySystemException) e).getCode())
                        .isEqualTo(ExternalAuthClient.ACCOUNT_NOT_FOUND));
        verify(localAuthenticator, never()).authenticate(any());
    }

    @Test
    @DisplayName("密码错误：不回退本地，直接透传外部错误码")
    void doesNotFallbackOnWrongPassword() {
        properties.setEnabled(true);
        when(externalAuthenticator.authenticate(any()))
                .thenThrow(new NwaySystemException(1010, "工号或密码错误"));

        assertThatThrownBy(() -> authorization.authenticate(loginDto()))
                .isInstanceOf(NwaySystemException.class)
                .hasMessage("工号或密码错误");
        verify(localAuthenticator, never()).authenticate(any());
    }

    @Test
    @DisplayName("登录方式声明为 password")
    void declaresPasswordLoginType() {
        assertThat(authorization.loginType()).isEqualTo(LoginTypeEnum.PASSWORD);
    }

    private static LoginUser loginUser(String employeeId) {
        LoginUser user = new LoginUser();
        user.setEmployeeId(employeeId);
        return user;
    }

    private static LoginDto loginDto() {
        LoginDto dto = new LoginDto();
        dto.setName("10086");
        dto.setKey("pwd");
        dto.setLoginType("password");
        return dto;
    }
}
