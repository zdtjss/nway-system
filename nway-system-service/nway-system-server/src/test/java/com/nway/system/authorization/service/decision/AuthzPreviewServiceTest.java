package com.nway.system.authorization.service.decision;

import com.nway.system.identity.entity.User;
import com.nway.system.authorization.dao.GrantDao;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.identity.dao.SysUserPositionDao;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.UserRole;
import com.nway.system.identity.dao.UserDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 变更预演：影响人数与动作增减。
 */
class AuthzPreviewServiceTest {

    private final GrantDao grantDao = mock(GrantDao.class);
    private final UserRoleDao userRoleDao = mock(UserRoleDao.class);
    private final SysUserPositionDao positionDao = mock(SysUserPositionDao.class);
    private final UserDao userDao = mock(UserDao.class);
    private final AuthzPreviewService service =
            new AuthzPreviewService(grantDao, userRoleDao, positionDao, userDao);

    @Test
    @DisplayName("角色变更：影响角色下用户，并给出动作增减/新增 DENY")
    void previewRoleChange() {
        when(userRoleDao.queryByRole(List.of("r1"))).thenReturn(List.of(
                binding("E1"), binding("E2"), binding("E1")));
        when(grantDao.queryBySubject("ROLE", "r1")).thenReturn(List.of(
                grant("user", "[\"view\",\"create\"]", SysGrant.EFFECT_ALLOW)));

        AuthzPreviewService.PreviewResult result = service.preview("ROLE", "r1", List.of(
                grant("user", "[\"view\",\"delete\"]", SysGrant.EFFECT_ALLOW),
                grant("user", "[\"delete\"]", SysGrant.EFFECT_DENY)));

        assertThat(result.affectedUsers()).isEqualTo(2);
        assertThat(result.sampleUsers()).containsExactlyInAnyOrder("E1", "E2");
        assertThat(result.diffs()).hasSize(1);
        AuthzPreviewService.DiffItem diff = result.diffs().get(0);
        assertThat(diff.added()).containsExactly("delete");
        assertThat(diff.removed()).containsExactly("create");
        assertThat(diff.denied()).containsExactly("delete");
    }

    @Test
    @DisplayName("用户主体：影响人数为 1")
    void previewUserChange() {
        when(userDao.getByEmployeeId("E1")).thenReturn(user("E1"));
        when(grantDao.queryBySubject("USER", "E1")).thenReturn(List.of());

        AuthzPreviewService.PreviewResult result = service.preview("USER", "E1",
                List.of(grant("order", "[\"read\"]", SysGrant.EFFECT_ALLOW)));

        assertThat(result.affectedUsers()).isEqualTo(1);
        assertThat(result.diffs().get(0).added()).containsExactly("read");
    }

    private static UserRole binding(String employeeId) {
        UserRole ur = new UserRole();
        ur.setEmployeeId(employeeId);
        ur.setRoleId("r1");
        return ur;
    }

    private static SysGrant grant(String resource, String actions, String effect) {
        SysGrant grant = new SysGrant();
        grant.setAppCode("nway-system");
        grant.setResourceCode(resource);
        grant.setActions(actions);
        grant.setEffect(effect);
        grant.setDataScope(SysGrant.SCOPE_SELF);
        return grant;
    }

    private static com.nway.system.identity.entity.User user(String employeeId) {
        com.nway.system.identity.entity.User user = new com.nway.system.identity.entity.User();
        user.setEmployeeId(employeeId);
        return user;
    }
}
