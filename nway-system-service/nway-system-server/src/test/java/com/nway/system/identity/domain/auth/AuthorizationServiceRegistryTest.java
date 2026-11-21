package com.nway.system.identity.domain.auth;

import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.identity.domain.enums.LoginTypeEnum;
import com.nway.system.common.exception.NwaySystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthorizationServiceRegistry：登录方式分发")
class AuthorizationServiceRegistryTest {

    private final AuthorizationService passwordService = service(LoginTypeEnum.PASSWORD);
    private final AuthorizationService bioService = service(LoginTypeEnum.BIO);

    @Test
    @DisplayName("按登录方式分发到对应实现")
    void dispatchesByLoginType() {
        AuthorizationServiceRegistry registry = new AuthorizationServiceRegistry(List.of(passwordService, bioService));

        assertThat(registry.get("password")).isSameAs(passwordService);
        assertThat(registry.get("bio")).isSameAs(bioService);
    }

    @Test
    @DisplayName("兼容历史 loginType=external：等同 password")
    void treatsLegacyExternalAsPassword() {
        AuthorizationServiceRegistry registry = new AuthorizationServiceRegistry(List.of(passwordService, bioService));

        assertThat(registry.get("external")).isSameAs(passwordService);
    }

    @Test
    @DisplayName("未登记的登录方式：拒绝")
    void rejectsUnknownLoginType() {
        AuthorizationServiceRegistry registry = new AuthorizationServiceRegistry(List.of(passwordService, bioService));

        assertThatThrownBy(() -> registry.get("weixin"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("无效的登录类型");
    }

    @Test
    @DisplayName("同一登录方式重复注册：启动即失败")
    void rejectsDuplicateRegistration() {
        AuthorizationService duplicate = service(LoginTypeEnum.PASSWORD);

        assertThatThrownBy(() -> new AuthorizationServiceRegistry(List.of(passwordService, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复注册");
    }

    private static AuthorizationService service(LoginTypeEnum loginType) {
        return new AuthorizationService() {
            @Override
            public LoginTypeEnum loginType() {
                return loginType;
            }

            @Override
            public LoginUser authenticate(LoginDto loginDTO) {
                return new LoginUser();
            }
        };
    }
}
