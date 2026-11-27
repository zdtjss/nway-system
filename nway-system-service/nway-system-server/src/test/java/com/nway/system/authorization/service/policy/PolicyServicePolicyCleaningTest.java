package com.nway.system.authorization.service.policy;

import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.authorization.service.grant.GrantActionAdvisor;
import com.nway.system.authorization.service.grant.GrantCacheService;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.authorization.dao.PermissionPolicyDao;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.PermissionPolicy;
import com.nway.system.identity.dao.SysPositionDao;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 策略保存清洗单元测试：tenant_code 由主体推导，不接受调用方传入（审计 P0-2）。
 */
@DisplayName("PolicyService：策略保存的租户清洗")
class PolicyServicePolicyCleaningTest {

    private final PermissionPolicyDao policyDao = mock(PermissionPolicyDao.class);
    private final AppDao appDao = mock(AppDao.class);
    private final MenuDao menuDao = mock(MenuDao.class);
    private final ResourceDao resourceDao = mock(ResourceDao.class);
    private final RoleDao roleDao = mock(RoleDao.class);
    private final UserDao userDao = mock(UserDao.class);
    private final SysPositionDao positionDao = mock(SysPositionDao.class);
    private final UserRoleDao userRoleDao = mock(UserRoleDao.class);
    private final PolicyCacheManager policyCacheManager = mock(PolicyCacheManager.class);
    private final com.nway.system.authorization.service.grant.AuthzVersionService authzVersionService =
            mock(com.nway.system.authorization.service.grant.AuthzVersionService.class);
    private final com.nway.system.authorization.service.grant.GrantCacheService grantCacheService =
            mock(com.nway.system.authorization.service.grant.GrantCacheService.class);
    private final com.nway.system.authorization.service.grant.GrantActionAdvisor grantActionAdvisor =
            mock(com.nway.system.authorization.service.grant.GrantActionAdvisor.class);

    private PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService();
        ReflectionTestUtils.setField(service, "policyDao", policyDao);
        ReflectionTestUtils.setField(service, "appDao", appDao);
        ReflectionTestUtils.setField(service, "menuDao", menuDao);
        ReflectionTestUtils.setField(service, "resourceDao", resourceDao);
        ReflectionTestUtils.setField(service, "roleDao", roleDao);
        ReflectionTestUtils.setField(service, "userDao", userDao);
        ReflectionTestUtils.setField(service, "positionDao", positionDao);
        ReflectionTestUtils.setField(service, "userRoleDao", userRoleDao);
        ReflectionTestUtils.setField(service, "policyCacheManager", policyCacheManager);
        ReflectionTestUtils.setField(service, "authzVersionService", authzVersionService);
        ReflectionTestUtils.setField(service, "grantCacheService", grantCacheService);
        ReflectionTestUtils.setField(service, "grantActionAdvisor", grantActionAdvisor);
        // 默认资源存在：单测聚焦租户/应用清洗，资源存在性另有专门用例
        when(menuDao.existsPageResource(anyString(), anyString())).thenReturn(true);
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode("OA");
        user.setSuperAdmin(false);
        user.setAdmin(false);
        user.setAdminTenantCodes(List.of());
        LoginUserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    private static User user(String employeeId, String tenantCode) {
        User user = new User();
        user.setEmployeeId(employeeId);
        user.setTenantCode(tenantCode);
        return user;
    }

    private static PermissionPolicy policy(String subjectType, String subjectId, String tenantCode) {
        PermissionPolicy policy = new PermissionPolicy();
        policy.setSubjectType(subjectType);
        policy.setSubjectId(subjectId);
        policy.setAppCode("wform");
        policy.setResourceCode("wform:data");
        policy.setTenantCode(tenantCode);
        return policy;
    }

    private static Role role(String id, String appCode) {
        Role role = new Role();
        role.setId(id);
        role.setAppCode(appCode);
        role.setTenantCode("OA");
        return role;
    }

    private static App app(String appCode, String tenantCode) {
        App app = new App();
        app.setAppCode(appCode);
        app.setTenantCode(tenantCode);
        return app;
    }

    private PermissionPolicy insertedPolicy() {
        ArgumentCaptor<PermissionPolicy> captor = ArgumentCaptor.forClass(PermissionPolicy.class);
        verify(policyDao).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("USER 主体：租户被强制为用户的租户，请求体传的 public 被忽略")
    void userSubjectTenantIsDerivedFromUser() {
        when(userDao.getByEmployeeId("oa")).thenReturn(user("oa", "OA"));

        service.save(policy("USER", "oa", "public"));

        assertThat(insertedPolicy().getTenantCode()).isEqualTo("OA");
    }

    @Test
    @DisplayName("APP 主体：单条保存入口拒绝，须走 saveAppPolicies")
    void appSubjectRejected() {
        assertThatThrownBy(() -> service.save(policy("APP", "wform", "public")))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("saveAppPolicies");
    }

    @Test
    @DisplayName("主体不存在：拒绝写入")
    void unknownSubjectRejected() {
        when(userDao.getByEmployeeId("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.save(policy("USER", "ghost", "OA")))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("更新：原记录租户与主体租户不一致时拒绝（防止改租户挪进 public）")
    void updateAcrossTenantRejected() {
        when(userDao.getByEmployeeId("oa")).thenReturn(user("oa", "OA"));
        PermissionPolicy existing = policy("USER", "oa", "public");
        existing.setId("p1");
        when(policyDao.getById("p1")).thenReturn(existing);

        PermissionPolicy update = policy("USER", "oa", "OA");
        update.setId("p1");

        assertThatThrownBy(() -> service.save(update))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    @DisplayName("ROLE 主体：同租户跨应用放行（wform 角色授权本租户低代码应用）")
    void roleSubjectCrossAppInSameTenantAllowed() {
        when(roleDao.queryById("r_oa_wf")).thenReturn(role("r_oa_wf", "wform"));
        when(appDao.getByCode("oaapp")).thenReturn(app("oaapp", "OA"));

        PermissionPolicy p = policy("ROLE", "r_oa_wf", "OA");
        p.setAppCode("oaapp");

        service.save(p);

        assertThat(insertedPolicy().getAppCode()).isEqualTo("oaapp");
    }

    @Test
    @DisplayName("ROLE 主体：public 共享应用放行")
    void roleSubjectPublicAppAllowed() {
        when(roleDao.queryById("r_oa_app")).thenReturn(role("r_oa_app", "oaapp"));
        when(appDao.getByCode("wform")).thenReturn(app("wform", "public"));

        service.save(policy("ROLE", "r_oa_app", "OA"));

        assertThat(insertedPolicy().getAppCode()).isEqualTo("wform");
    }

    @Test
    @DisplayName("ROLE 主体：其他租户私有应用拒绝（策略永不可达）")
    void roleSubjectOtherTenantAppRejected() {
        when(roleDao.queryById("r_oa_wf")).thenReturn(role("r_oa_wf", "wform"));
        when(appDao.getByCode("otherapp")).thenReturn(app("otherapp", "OTHER"));

        PermissionPolicy p = policy("ROLE", "r_oa_wf", "OA");
        p.setAppCode("otherapp");

        assertThatThrownBy(() -> service.save(p))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("可授权范围");
    }

    @Test
    @DisplayName("ROLE 主体：平台级角色（app_code='*'）不受应用一致性约束")
    void platformLevelRoleAllowsAnyApp() {
        when(roleDao.queryById("r_admin")).thenReturn(role("r_admin", "*"));

        service.save(policy("ROLE", "r_admin", "OA"));

        assertThat(insertedPolicy().getAppCode()).isEqualTo("wform");
    }

    @Test
    @DisplayName("资源不存在：拒绝写入（避免产出永不生效的孤儿策略）")
    void orphanResourceRejected() {
        when(userDao.getByEmployeeId("oa")).thenReturn(user("oa", "OA"));
        when(menuDao.existsPageResource("wform", "wform:data")).thenReturn(false);
        when(resourceDao.exists("wform", "wform:data")).thenReturn(false);

        assertThatThrownBy(() -> service.save(policy("USER", "oa", "OA")))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("资源不存在");
    }

    @Test
    @DisplayName("资源登记台已登记的非菜单资源（如 AI 接口 ai:xxx）：允许写入")
    void catalogResourceWithoutMenuAllowed() {
        when(userDao.getByEmployeeId("oa")).thenReturn(user("oa", "OA"));
        when(menuDao.existsPageResource("nway-system", "ai:bpm_my_task")).thenReturn(false);
        when(resourceDao.exists("nway-system", "ai:bpm_my_task")).thenReturn(true);

        PermissionPolicy p = policy("USER", "oa", "OA");
        p.setAppCode("nway-system");
        p.setResourceCode("ai:bpm_my_task");

        service.save(p);

        assertThat(insertedPolicy().getResourceCode()).isEqualTo("ai:bpm_my_task");
    }

    @Test
    @DisplayName("资源通配符 '*'：跳过资源存在性校验直接写入")
    void wildcardResourceSkipsExistenceCheck() {
        when(userDao.getByEmployeeId("oa")).thenReturn(user("oa", "OA"));
        PermissionPolicy p = policy("USER", "oa", "OA");
        p.setResourceCode("*");

        service.save(p);

        assertThat(insertedPolicy().getResourceCode()).isEqualTo("*");
        verify(menuDao, never()).existsPageResource(anyString(), anyString());
    }
}
