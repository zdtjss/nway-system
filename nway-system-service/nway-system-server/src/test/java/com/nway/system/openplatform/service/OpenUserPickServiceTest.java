package com.nway.system.openplatform.service;

import com.nway.spring.jdbc.pagination.Page;
import com.nway.system.common.web.PageVo;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import com.nway.system.identity.web.model.UserQuery;
import com.nway.system.openplatform.web.model.OpenUserPickVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OpenUserPickService 开放平台选人搜索测试")
class OpenUserPickServiceTest {

    private final UserDao userDao = mock(UserDao.class);
    private final SysDeptDao sysDeptDao = mock(SysDeptDao.class);
    private final OpenDataScopeService openDataScopeService = mock(OpenDataScopeService.class);

    private OpenUserPickService service;

    @BeforeEach
    void setUp() {
        service = new OpenUserPickService();
        ReflectionTestUtils.setField(service, "userDao", userDao);
        ReflectionTestUtils.setField(service, "sysDeptDao", sysDeptDao);
        ReflectionTestUtils.setField(service, "openDataScopeService", openDataScopeService);
    }

    private void stubScope(OpenDataScopeService.OpenDataScope scope) {
        when(openDataScopeService.resolve()).thenReturn(scope);
    }

    private void stubUsers(List<User> users, int total) {
        Page<User> page = new Page<>();
        page.setPageData(users);
        page.setTotal(total);
        when(userDao.queryPage(any(UserQuery.class))).thenReturn(page);
    }

    private static SysDept dept(String id, String deptCode, String deptName) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setDeptCode(deptCode);
        dept.setDeptName(deptName);
        return dept;
    }

    @Test
    @DisplayName("分页归一与关键字标记：size 夹紧 100、page 最小 1、关键字匹配手机号")
    void pageSizeClampedAndKeywordFlags() {
        stubScope(OpenDataScopeService.OpenDataScope.ALL);
        stubUsers(List.of(), 0);

        service.search("138", null, false, 0, 500);

        ArgumentCaptor<UserQuery> captor = ArgumentCaptor.forClass(UserQuery.class);
        verify(userDao).queryPage(captor.capture());
        UserQuery query = captor.getValue();
        assertThat(query.getCurrent()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(100);
        assertThat(query.getKeyword()).isEqualTo("138");
        assertThat(query.isKeywordMatchPhone()).isTrue();
        assertThat(query.getStatus()).isEqualTo("using");
    }

    @Test
    @DisplayName("关键字为空且范围为 ALL 时不加部门与关键字条件")
    void blankKeywordAndUnrestrictedScope() {
        stubScope(OpenDataScopeService.OpenDataScope.ALL);
        stubUsers(List.of(), 0);

        service.search("  ", null, false, 1, 15);

        ArgumentCaptor<UserQuery> captor = ArgumentCaptor.forClass(UserQuery.class);
        verify(userDao).queryPage(captor.capture());
        assertThat(captor.getValue().getKeyword()).isNull();
        assertThat(captor.getValue().getDeptIdIn()).isNull();
    }

    @Test
    @DisplayName("includeSub=true 时按 deptCode 展开子树")
    void includeSubExpandsSubTree() {
        stubScope(OpenDataScopeService.OpenDataScope.ALL);
        stubUsers(List.of(), 0);
        when(sysDeptDao.queryById("D1")).thenReturn(dept("D1", "01", "总部"));
        when(sysDeptDao.querySubTree("01")).thenReturn(List.of(
                dept("D1", "01", null),
                dept("D2", "0102", null)));

        service.search(null, "D1", true, 1, 15);

        ArgumentCaptor<UserQuery> captor = ArgumentCaptor.forClass(UserQuery.class);
        verify(userDao).queryPage(captor.capture());
        assertThat(captor.getValue().getDeptIdIn()).containsExactly("D1", "D2");
    }

    @Test
    @DisplayName("请求部门不在数据范围内 → 返回空页且不查库（fail-closed）")
    void deptOutOfScopeReturnsEmptyPage() {
        stubScope(OpenDataScopeService.OpenDataScope.ofDeptIds(List.of("D9")));

        PageVo<OpenUserPickVo> page = service.search(null, "D1", false, 1, 15);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getData()).isEmpty();
        verify(userDao, never()).queryPage(any(UserQuery.class));
    }

    @Test
    @DisplayName("SELF 范围仅保留本人，并回填部门名称与手机号明文")
    void selfOnlyKeepsSelfAndFillsDeptName() {
        stubScope(new OpenDataScopeService.OpenDataScope(false, Set.of("D1"), "EMP001"));
        User self = new User();
        self.setEmployeeId("EMP001");
        self.setRealName("张三");
        self.setPhoneNumber("13800001001");
        self.setDeptId("D1");
        User other = new User();
        other.setEmployeeId("EMP002");
        other.setRealName("李四");
        other.setDeptId("D1");
        stubUsers(List.of(self, other), 2);
        when(sysDeptDao.queryByIds(List.of("D1")))
                .thenReturn(List.of(dept("D1", "01", "技术研发部")));

        PageVo<OpenUserPickVo> page = service.search(null, null, false, 1, 15);

        assertThat(page.getData()).hasSize(1);
        OpenUserPickVo vo = page.getData().get(0);
        assertThat(vo.getEmployeeId()).isEqualTo("EMP001");
        assertThat(vo.getPhoneNumber()).isEqualTo("13800001001");
        assertThat(vo.getDeptName()).isEqualTo("技术研发部");
    }
}