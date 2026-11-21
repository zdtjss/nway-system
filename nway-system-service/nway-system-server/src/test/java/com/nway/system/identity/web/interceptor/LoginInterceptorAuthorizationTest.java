package com.nway.system.identity.web.interceptor;

import com.nway.system.identity.service.LoginService;
import com.nway.system.tenant.support.TenantStatusCache;
import com.nway.system.tenant.support.AdminGrantCacheManager;
import com.nway.system.tenant.web.model.AdminGrantDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录拦截器的授权来源与顺序。
 * <p>
 * 核心约束：<b>超管/管理员资格只认库侧缓存，不认 JWT 声明</b>；并且必须先补齐
 * {@code superAdmin} 再判租户生命周期，否则刚被降权的用户仍能绕过租户禁用校验。
 */
class LoginInterceptorAuthorizationTest {

    private final LoginService loginService = mock(LoginService.class);
    private final TenantStatusCache tenantStatusCache = mock(TenantStatusCache.class);
    private final AdminGrantCacheManager adminGrantCacheManager = mock(AdminGrantCacheManager.class);

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    private LoginInterceptor interceptor;

    @BeforeEach
    void setUp() {
        LoginInterceptor.Config config = new LoginInterceptor.Config();
        config.setAnonymous(new HashSet<>());

        interceptor = new LoginInterceptor();
        ReflectionTestUtils.setField(interceptor, "loginService", loginService);
        ReflectionTestUtils.setField(interceptor, "contextPath", "/nway-system");
        ReflectionTestUtils.setField(interceptor, "config", config);
        ReflectionTestUtils.setField(interceptor, "adminGrantCacheManager", adminGrantCacheManager);
        ReflectionTestUtils.setField(interceptor, "tenantStatusCache", tenantStatusCache);

        when(request.getHeader("Authorization")).thenReturn("Bearer t");
        when(loginService.checkTokenVersion("E1", 0)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    @Test
    @DisplayName("Token 声明超管但库侧已降权：以库为准，降权立即生效")
    void databaseWinsOverTokenClaim() throws Exception {
        when(loginService.parseLoginUserFromJwt("t")).thenReturn(loginUser(true));
        when(adminGrantCacheManager.get("E1")).thenReturn(grant(false, false));
        when(tenantStatusCache.isUsable("t1")).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(LoginUserContext.current().isSuperAdmin()).isFalse();
    }

    @DisplayName("Token 声明非超管但库侧已授权：以库为准")
    @Test
    void databaseGrantsSuperAdmin() throws Exception {
        when(loginService.parseLoginUserFromJwt("t")).thenReturn(loginUser(false));
        when(adminGrantCacheManager.get("E1")).thenReturn(grant(true, false));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(LoginUserContext.current().isSuperAdmin()).isTrue();
        // 超管不参与租户生命周期校验
        verify(tenantStatusCache, never()).isUsable(anyString());
    }

    @Test
    @DisplayName("降权后租户已禁用：必须 403（此前会用 Token 里的 superAdmin 绕过校验）")
    void demotedSuperAdminCannotBypassTenantCheck() throws Exception {
        when(loginService.parseLoginUserFromJwt("t")).thenReturn(loginUser(true));
        when(adminGrantCacheManager.get("E1")).thenReturn(grant(false, false));
        when(tenantStatusCache.isUsable("t1")).thenReturn(false);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(anyInt(), anyString());
        assertThat(LoginUserContext.current()).isNull();
    }

    @Test
    @DisplayName("管理员标志与授权租户同样以库侧缓存为准")
    void adminFlagsComeFromCache() throws Exception {
        when(loginService.parseLoginUserFromJwt("t")).thenReturn(loginUser(false));

        AdminGrantDto cached = grant(false, true);
        cached.setTenantCodes(List.of("t1", "t2"));
        when(adminGrantCacheManager.get("E1")).thenReturn(cached);
        when(tenantStatusCache.isUsable("t1")).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(LoginUserContext.current().isAdmin()).isTrue();
        assertThat(LoginUserContext.current().getAdminTenantCodes()).containsExactly("t1", "t2");
    }

    private static LoginUser loginUser(boolean superAdminFromToken) {
        LoginUser user = new LoginUser();
        user.setEmployeeId("E1");
        user.setTenantCode("t1");
        user.setTokenVersion(0);
        user.setSuperAdmin(superAdminFromToken);
        return user;
    }

    private static AdminGrantDto grant(boolean superAdmin, boolean admin) {
        AdminGrantDto dto = new AdminGrantDto();
        dto.setEmployeeId("E1");
        dto.setSuperAdmin(superAdmin);
        dto.setAdmin(admin);
        dto.setTenantCodes(List.of());
        return dto;
    }
}
