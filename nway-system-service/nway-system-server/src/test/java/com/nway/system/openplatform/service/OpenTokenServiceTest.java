package com.nway.system.openplatform.service;

import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.config.properties.JwtProperties;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.JwtUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.openplatform.entity.SysOpenApp;
import com.nway.system.authorization.entity.PermissionPolicy;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 开放平台 Token 签发测试（作用域租户 + APP 策略派生 scope）。
 */
@DisplayName("OpenTokenService：targetTenantCode 作用域签发")
class OpenTokenServiceTest {

    private static final String SECRET = "nway-system-open-secret-key-for-unit-test-0123456789";

    private final OpenAppService openAppService = mock(OpenAppService.class);
    private final OpenSsoService openSsoService = mock(OpenSsoService.class);
    private final OpenTenantScopeResolver scopeResolver = mock(OpenTenantScopeResolver.class);
    private final PolicyService policyService = mock(PolicyService.class);

    private JwtProperties jwtProperties;
    private OpenTokenService service;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        jwtProperties.setOpenSecret(SECRET);
        service = new OpenTokenService();
        ReflectionTestUtils.setField(service, "openAppService", openAppService);
        ReflectionTestUtils.setField(service, "openSsoService", openSsoService);
        ReflectionTestUtils.setField(service, "jwtProperties", jwtProperties);
        ReflectionTestUtils.setField(service, "openTenantScopeResolver", scopeResolver);
        ReflectionTestUtils.setField(service, "policyService", policyService);
    }

    private static SysOpenApp app(String appCode, String tenantCode) {
        SysOpenApp app = new SysOpenApp();
        app.setAppCode(appCode);
        app.setAppSecret("secret");
        app.setStatus("using");
        app.setTenantCode(tenantCode);
        app.setGrantedScopes("[\"menu:write\"]");
        app.setDataScopeType("ALL");
        return app;
    }

    private static String userToken(String employeeId) {
        return JwtUtil.generateToken(employeeId, "a", false, List.of(), SECRET);
    }

    private String issuedTenant(SysOpenApp app, String targetTenantCode) {
        when(openAppService.getByCode(app.getAppCode())).thenReturn(app);
        when(openAppService.verifySecret(any(), any())).thenReturn(true);
        Map<String, Object> result = service.issueToken(app.getAppCode(), "secret", "user_delegate",
                userToken("e001"), null, targetTenantCode);
        Claims claims = JwtUtil.parseToken((String) result.get("access_token"), SECRET);
        assertThat(claims).isNotNull();
        return JwtUtil.getTenantCode(claims);
    }

    @Test
    @DisplayName("不传 targetTenantCode：作用域=凭据应用租户（改造前语义不变）")
    void defaultsToAppTenant() {
        when(scopeResolver.canManage("e001", "a")).thenReturn(true);
        assertThat(issuedTenant(app("wform", "a"), null)).isEqualTo("a");
    }

    @Test
    @DisplayName("显式传可管理的 targetTenantCode：作用域=目标租户（凭据应用仍为原租户）")
    void honorsManageableTargetTenant() {
        when(scopeResolver.canManage("e001", "a")).thenReturn(true);
        when(scopeResolver.canManage("e001", "b")).thenReturn(true);
        assertThat(issuedTenant(app("wform", "a"), "b")).isEqualTo("b");
    }

    @Test
    @DisplayName("public 凭据应用 + 显式可管理作用域：允许跨租户代理（平台级集成应用）")
    void publicCredentialAppAllowsCrossTenantScope() {
        when(scopeResolver.canManage("e001", "b")).thenReturn(true);
        assertThat(issuedTenant(app("wform", TenantUtil.PUBLIC_TENANT_CODE), "b")).isEqualTo("b");
    }

    @Test
    @DisplayName("public 凭据应用 + 不可管理的作用域：仍拒绝（4.2 恒不放宽）")
    void publicCredentialAppRejectsUnmanageableTarget() {
        when(openAppService.getByCode("wform")).thenReturn(app("wform", TenantUtil.PUBLIC_TENANT_CODE));
        when(openAppService.verifySecret(any(), any())).thenReturn(true);
        when(scopeResolver.canManage("e001", "c")).thenReturn(false);

        assertThatThrownBy(() -> service.issueToken("wform", "secret", "user_delegate",
                userToken("e001"), null, "c"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("无权以该租户为数据作用域签发 Token");
    }

    @Test
    @DisplayName("非 public 凭据应用跨租户：仍拒绝（放宽仅限 public 集成应用）")
    void nonPublicCredentialAppRejectsCrossTenantScope() {
        when(openAppService.getByCode("wform")).thenReturn(app("wform", "a"));
        when(openAppService.verifySecret(any(), any())).thenReturn(true);
        when(scopeResolver.canManage("e001", "a")).thenReturn(false);
        when(scopeResolver.canManage("e001", "b")).thenReturn(true);

        assertThatThrownBy(() -> service.issueToken("wform", "secret", "user_delegate",
                userToken("e001"), null, "b"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("禁止代理授权");
    }

    @Test
    @DisplayName("public 凭据应用不传作用域：仍按原规则校验（不可管理 public 则拒绝）")
    void publicCredentialAppWithoutTargetKeepsOriginalCheck() {
        when(openAppService.getByCode("wform")).thenReturn(app("wform", TenantUtil.PUBLIC_TENANT_CODE));
        when(openAppService.verifySecret(any(), any())).thenReturn(true);
        when(scopeResolver.canManage("e001", TenantUtil.PUBLIC_TENANT_CODE)).thenReturn(false);

        assertThatThrownBy(() -> service.issueToken("wform", "secret", "user_delegate",
                userToken("e001"), null, null))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("禁止代理授权");
    }

    @Test
    @DisplayName("目标租户不可管理：拒绝签发")
    void rejectsUnmanageableTargetTenant() {
        when(openAppService.getByCode("wform")).thenReturn(app("wform", "a"));
        when(openAppService.verifySecret(any(), any())).thenReturn(true);
        when(scopeResolver.canManage("e001", "a")).thenReturn(true);
        when(scopeResolver.canManage("e001", "c")).thenReturn(false);

        assertThatThrownBy(() -> service.issueToken("wform", "secret", "user_delegate",
                userToken("e001"), null, "c"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("无权以该租户为数据作用域签发 Token");
    }

    @Test
    @DisplayName("凭据应用租户不在代理用户可管理范围：拒绝签发")
    void rejectsUnmanageableCredentialApp() {
        when(openAppService.getByCode("wform")).thenReturn(app("wform", "a"));
        when(openAppService.verifySecret(any(), any())).thenReturn(true);
        when(scopeResolver.canManage("e001", "a")).thenReturn(false);

        assertThatThrownBy(() -> service.issueToken("wform", "secret", "user_delegate",
                userToken("e001"), null, null))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("禁止代理授权");
    }

    @Test
    @DisplayName("client_credentials 不接受与凭据应用租户不同的作用域租户")
    void rejectsScopeForClientCredentials() {
        when(openAppService.getByCode("wform")).thenReturn(app("wform", "a"));
        when(openAppService.verifySecret(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.issueToken("wform", "secret", "client_credentials",
                null, null, "b"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("仅 user_delegate 模式支持指定数据作用域租户");
    }

    @Test
    @DisplayName("client_credentials：作用域恒为应用所属租户")
    void clientCredentialsKeepsAppTenant() {
        when(openAppService.getByCode("wform")).thenReturn(app("wform", "a"));
        when(openAppService.verifySecret(any(), any())).thenReturn(true);

        Map<String, Object> result = service.issueToken("wform", "secret", "client_credentials",
                null, null, null);
        Claims claims = JwtUtil.parseToken((String) result.get("access_token"), SECRET);
        assertThat(JwtUtil.getTenantCode(claims)).isEqualTo("a");
        assertThat(JwtUtil.getDelegateEmployeeId(claims)).isNull();
    }

    @Test
    @DisplayName("应用未归属租户：拒绝签发")
    void rejectsAppWithoutTenant() {
        when(openAppService.getByCode("wform")).thenReturn(app("wform", null));
        when(openAppService.verifySecret(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.issueToken("wform", "secret", "client_credentials",
                null, null, null))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("应用未归属租户");
    }

    @Test
    @DisplayName("缺省 target 且凭据应用租户 == 代理用户所属租户：行为与改造前一致")
    void defaultPathStaysCompatible() {
        when(scopeResolver.canManage(eq("e001"), eq("a"))).thenReturn(true);
        assertThat(issuedTenant(app("wform", "a"), "")).isEqualTo("a");
    }

    // ====== APP 主体策略 → grantedScopes（权限模型重构 S1-2） ======

    private List<String> issuedScopes(SysOpenApp app) {
        when(openAppService.getByCode(app.getAppCode())).thenReturn(app);
        when(openAppService.verifySecret(any(), any())).thenReturn(true);
        Map<String, Object> result = service.issueToken(app.getAppCode(), "secret", "client_credentials",
                null, null, null);
        Claims claims = JwtUtil.parseToken((String) result.get("access_token"), SECRET);
        assertThat(claims).isNotNull();
        return JwtUtil.getGrantedScopes(claims);
    }

    private static PermissionPolicy appPolicy(String resourceCode, String actionsJson) {
        PermissionPolicy policy = new PermissionPolicy();
        policy.setSubjectType("APP");
        policy.setAppCode("nway-system");
        policy.setResourceCode(resourceCode);
        policy.setActions(actionsJson);
        policy.setDataScope("SELF");
        return policy;
    }

    @Test
    @DisplayName("无 APP 策略：grantedScopes 与存量配置逐项一致（零行为变更）")
    void withoutAppPoliciesKeepsLegacyScopes() {
        when(policyService.queryByAppSubject("wform")).thenReturn(List.of());

        assertThat(issuedScopes(app("wform", "a"))).containsExactly("menu:write");
    }

    @Test
    @DisplayName("有 APP 策略：与存量 scope 取并集（增量为授权，不剥夺存量）")
    void appPoliciesAreMergedWithLegacyScopes() {
        when(policyService.queryByAppSubject("wform")).thenReturn(List.of(
                appPolicy("user", "[\"read\"]"),
                appPolicy("org", "[\"read\",\"write\"]")));

        assertThat(issuedScopes(app("wform", "a")))
                .containsExactlyInAnyOrder("menu:write", "user:read", "org:read", "org:write");
    }

    @Test
    @DisplayName("actions 为 null（全部操作）：展开为该资源的基础读写 scope")
    void nullActionsExpandToReadWrite() {
        when(policyService.queryByAppSubject("wform")).thenReturn(List.of(appPolicy("dict", null)));

        assertThat(issuedScopes(app("wform", "a")))
                .containsExactlyInAnyOrder("menu:write", "dict:read", "dict:write");
    }

    @Test
    @DisplayName("actions 为空数组：不派生任何 scope（显式不授予）")
    void emptyActionsProduceNoScope() {
        when(policyService.queryByAppSubject("wform")).thenReturn(List.of(appPolicy("dict", "[]")));

        assertThat(issuedScopes(app("wform", "a"))).containsExactly("menu:write");
    }

    @Test
    @DisplayName("资源为 * ：派生通配 scope（拦截器据此外放行任意 scope）")
    void wildcardResourceProducesWildcardScope() {
        when(policyService.queryByAppSubject("wform")).thenReturn(List.of(appPolicy("*", null)));

        assertThat(issuedScopes(app("wform", "a"))).contains("menu:write", "*");
    }

    @Test
    @DisplayName("同资源多条策略/重复动作：scope 去重")
    void duplicateScopesAreDeduplicated() {
        when(policyService.queryByAppSubject("wform")).thenReturn(List.of(
                appPolicy("user", "[\"read\"]"),
                appPolicy("user", "[\"read\"]")));

        assertThat(issuedScopes(app("wform", "a"))).containsExactly("menu:write", "user:read");
    }
}