package com.nway.system.identity.web.admin;

import com.nway.system.identity.service.LoginService;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.web.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * getLoginUser 的鉴权语义（D13）：无 Token / Token 失效统一 HTTP 401，
 * 不再出现“缺少请求头 400 / 无效 Token 200+null”的多套分支。
 */
@DisplayName("LoginController：当前用户接口 401 语义（D13）")
class LoginControllerTest {

    private LoginService loginService;
    private LoginController controller;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        controller = new LoginController();
        ReflectionTestUtils.setField(controller, "loginService", loginService);
    }

    @Test
    @DisplayName("缺少 Authorization 请求头：HTTP 401，body code=401，不返回用户数据")
    void missingHeaderReturns401() {
        ResponseEntity<Result<LoginUser>> response = controller.getLoginUser(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(401);
        assertThat(response.getBody().getMsg()).isEqualTo("未登录");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("空白 Token：HTTP 401，且不再调用登录服务")
    void blankTokenReturns401WithoutServiceCall() {
        ResponseEntity<Result<LoginUser>> response = controller.getLoginUser("   ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMsg()).isEqualTo("未登录");
        verify(loginService, never()).getLoginUser(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("过期/篡改 Token（服务解析为 null）：HTTP 401 而非 200+null")
    void invalidTokenReturns401() {
        when(loginService.getLoginUser("expired-token")).thenReturn(null);

        ResponseEntity<Result<LoginUser>> response = controller.getLoginUser("expired-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(401);
        assertThat(response.getBody().getMsg()).isEqualTo("登录已失效，请重新登录");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("有效 Token：HTTP 200，返回用户信息；Bearer 前缀被剥离")
    void validTokenReturns200() {
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        when(loginService.getLoginUser("valid-token")).thenReturn(user);

        ResponseEntity<Result<LoginUser>> response = controller.getLoginUser("Bearer valid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);
        assertThat(response.getBody().getData()).isSameAs(user);
        verify(loginService).getLoginUser("valid-token");
    }
}
