package com.nway.system.identity.service;

import com.nway.system.authorization.service.policy.PolicyCacheManager;
import com.nway.system.identity.web.model.RoleUserSaveDto;
import com.nway.system.identity.web.model.UserRoleSaveDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.entity.UserRole;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户-角色/角色-用户绑定变更后必须失效策略缓存。
 * <p>
 * 在此之前缓存 key 只按 employeeId 且无版本号，绑定变化要等 L1/L2 TTL（最长 10 分钟）才生效，
 * 表现为“管理员改了角色，用户仍然没有/仍然拥有权限”。
 */
class UserRoleServiceCacheEvictTest {

    private final UserRoleDao userRoleDao = mock(UserRoleDao.class);
    private final RoleDao roleDao = mock(RoleDao.class);
    private final UserDao userDao = mock(UserDao.class);
    private final PolicyCacheManager policyCacheManager = mock(PolicyCacheManager.class);

    private final UserRoleService service = new UserRoleService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "userRoleDao", userRoleDao);
        ReflectionTestUtils.setField(service, "roleDao", roleDao);
        ReflectionTestUtils.setField(service, "userDao", userDao);
        ReflectionTestUtils.setField(service, "policyCacheManager", policyCacheManager);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    @Test
    @DisplayName("按用户保存角色：失效该用户的策略缓存")
    void saveByUserEvictsTargetUser() {
        User user = new User();
        user.setEmployeeId("E1");
        user.setTenantCode("t1");
        when(userDao.getByEmployeeId("E1")).thenReturn(user);

        Role role = role("r1", "clerk", "t1");
        when(roleDao.queryByIds(anyList())).thenReturn(List.of(role));
        when(userRoleDao.queryByUser(List.of("E1"))).thenReturn(List.of());

        UserRoleSaveDto dto = new UserRoleSaveDto();
        dto.setEmployeeId("E1");
        dto.setRoleIds(List.of("r1"));
        service.save(dto);

        verify(policyCacheManager).evictAfterCommit("E1");
    }

    @Test
    @DisplayName("按角色保存用户：被移除与新增的用户都要失效缓存")
    void saveByRoleEvictsRemovedAndAddedUsers() {
        Role role = role("r1", "clerk", "t1");
        when(roleDao.queryById("r1")).thenReturn(role);

        User e2 = new User();
        e2.setEmployeeId("E2");
        e2.setTenantCode("t1");
        when(userDao.getByEmpids(List.of("E2"))).thenReturn(List.of(e2));

        when(userRoleDao.queryByRole(List.of("r1")))
                .thenReturn(List.of(binding("E1", "r1"), binding("E2", "r1")));

        RoleUserSaveDto dto = new RoleUserSaveDto();
        dto.setRoleId("r1");
        dto.setEmployeeIds(List.of("E2"));

        service.saveByRole(dto);

        verify(policyCacheManager).evictAfterCommit("E1");
        verify(policyCacheManager).evictAfterCommit("E2");
    }

    @Test
    @DisplayName("按用户保存角色：批量插入填充 creator_id/create_time（D15）")
    void saveByUserFillsAuditFields() {
        loginAsAdmin("E9", "t1");
        User user = new User();
        user.setEmployeeId("E1");
        user.setTenantCode("t1");
        when(userDao.getByEmployeeId("E1")).thenReturn(user);
        Role role = role("r1", "clerk", "t1");
        when(roleDao.queryByIds(anyList())).thenReturn(List.of(role));
        when(userRoleDao.queryByUser(List.of("E1"))).thenReturn(List.of());

        UserRoleSaveDto dto = new UserRoleSaveDto();
        dto.setEmployeeId("E1");
        dto.setRoleIds(List.of("r1"));
        service.save(dto);

        assertInsertedAudit("E9");
    }

    @Test
    @DisplayName("按角色保存用户：批量插入填充 creator_id/create_time（D15）")
    void saveByRoleFillsAuditFields() {
        loginAsAdmin("E9", "t1");
        Role role = role("r1", "clerk", "t1");
        when(roleDao.queryById("r1")).thenReturn(role);
        User e2 = new User();
        e2.setEmployeeId("E2");
        e2.setTenantCode("t1");
        when(userDao.getByEmpids(List.of("E2"))).thenReturn(List.of(e2));
        when(userRoleDao.queryByRole(List.of("r1"))).thenReturn(List.of());

        RoleUserSaveDto dto = new RoleUserSaveDto();
        dto.setRoleId("r1");
        dto.setEmployeeIds(List.of("E2"));
        service.saveByRole(dto);

        assertInsertedAudit("E9");
    }

    @SuppressWarnings("unchecked")
    private void assertInsertedAudit(String expectedOperator) {
        ArgumentCaptor<List<UserRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleDao).insertBatch(captor.capture());
        assertThat(captor.getValue()).isNotEmpty().allSatisfy(ur -> {
            assertThat(ur.getCreatorId()).isEqualTo(expectedOperator);
            assertThat(ur.getCreateTime()).isNotNull();
        });
    }

    private static void loginAsAdmin(String employeeId, String tenantCode) {
        LoginUser admin = new LoginUser();
        admin.setEmployeeId(employeeId);
        admin.setTenantCode(tenantCode);
        admin.setSuperAdmin(true);
        admin.setAdminTenantCodes(List.of());
        LoginUserContext.set(admin);
    }

    private static Role role(String id, String code, String tenant) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setTenantCode(tenant);
        role.setAppCode("wform");
        return role;
    }

    private static UserRole binding(String employeeId, String roleId) {
        UserRole ur = new UserRole();
        ur.setEmployeeId(employeeId);
        ur.setRoleId(roleId);
        return ur;
    }
}
