package com.nway.system.authorization.service.decision;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 由登录用户构建决策主体上下文。
 * <p>
 * 关键点：拦截器构造的 {@link LoginUser} 只有 JWT 载荷，没有部门信息；而 DEPT/DEPT_TREE
 * 数据范围必须拿到主部门编码。此处按工号回源（60 秒缓存），修掉"数据范围依赖未装载字段
 * 导致退化为不过滤"的缺陷。
 */
@Component
public class SubjectContextResolver {

    private final UserDao userDao;
    private final SysDeptDao deptDao;

    private final com.github.benmanes.caffeine.cache.Cache<String, List<String>> deptCache =
            Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(Duration.ofMinutes(1))
                    .build();

    public SubjectContextResolver(UserDao userDao, SysDeptDao deptDao) {
        this.userDao = userDao;
        this.deptDao = deptDao;
    }

    public SubjectContext from(LoginUser user) {
        if (user == null || StringUtils.isBlank(user.getEmployeeId())) {
            return null;
        }
        return new SubjectContext(user.getEmployeeId(), user.getTenantCode(),
                user.getCurrentWorkTenantCode(), user.isSuperAdmin(), deptCodes(user));
    }

    /** 按工号构建（授权诊断用，不依赖当前登录上下文）。 */
    public SubjectContext fromEmployeeId(String employeeId) {
        if (StringUtils.isBlank(employeeId)) {
            return null;
        }
        User user = TenantUtil.callAsSystem(() -> userDao.getByEmployeeId(employeeId));
        if (user == null) {
            return null;
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setEmployeeId(user.getEmployeeId());
        loginUser.setTenantCode(user.getTenantCode());
        loginUser.setSuperAdmin(Boolean.TRUE.equals(user.getIsSuperAdmin()));
        return new SubjectContext(employeeId, user.getTenantCode(), null,
                Boolean.TRUE.equals(user.getIsSuperAdmin()), deptCodes(loginUser));
    }

    private List<String> deptCodes(LoginUser user) {
        if (StringUtils.isNotBlank(user.getMainDeptCode())) {
            return List.of(user.getMainDeptCode());
        }
        return deptCache.get(user.getEmployeeId(), this::loadDeptCodes);
    }

    private List<String> loadDeptCodes(String employeeId) {
        User user = TenantUtil.callAsSystem(() -> userDao.getByEmployeeId(employeeId));
        if (user == null || StringUtils.isBlank(user.getDeptId())) {
            return List.of();
        }
        SysDept dept = TenantUtil.callAsSystem(() -> deptDao.queryById(user.getDeptId()));
        if (dept == null || StringUtils.isBlank(dept.getDeptCode())) {
            return List.of();
        }
        return List.of(dept.getDeptCode());
    }
}
