package com.nway.system.identity.dao;

import com.google.common.collect.Lists;
import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.spring.jdbc.sql.builder.UpdateBuilder;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.identity.entity.User;
import com.nway.system.identity.web.model.UserQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Repository
public class UserDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public Page<User> queryPage(UserQuery query) {
        QueryBuilder queryBuilder = SQL.query(User.class).ignoreInvalidDeep(true)
                .eq(User::getEmployeeId, query.getEmployeeId())
                .like(User::getRealName, query.getRealName())
                .eq(User::getStatus, query.getStatus())
                .eq(User::getTenantCode, query.getTenantCode())
                .in(User::getDeptId, query.getDeptIdIn())
                .eq(User::getCreatorId, query.getCreatorId());
        // 关键字：姓名或工号模糊搜索（管理员任命等场景）；开放平台选人搜索额外支持手机号匹配
        if (StringUtils.hasText(query.getKeyword())) {
            String like = "%" + query.getKeyword() + "%";
            if (query.isKeywordMatchPhone()) {
                queryBuilder.and(e -> e.like(User::getEmployeeId, like).or().like(User::getRealName, like)
                        .or().like(User::getPhoneNumber, like));
            } else {
                queryBuilder.and(e -> e.like(User::getEmployeeId, like).or().like(User::getRealName, like));
            }
        }
        return sqlExecutor.queryPage(queryBuilder, query.getCurrent(), query.getSize());
    }

    public void insert(User user) {
        sqlExecutor.insert(user);
    }

    public User getById(String id) {
        return sqlExecutor.queryById(id, User.class);
    }

    public List<User> getByEmpids(Collection<String> empids) {
        if (CollectionUtils.isEmpty(empids)) {
            return Lists.newArrayList();
        }
        QueryBuilder queryBuilder = SQL.query(User.class).in(User::getEmployeeId, empids);
        return sqlExecutor.queryList(queryBuilder);
    }

    public User getByEmployeeId(String employeeId) {
        QueryBuilder queryBuilder = SQL.query(User.class)
                .eq(User::getEmployeeId, employeeId);
        return sqlExecutor.queryFirst(queryBuilder);
    }

    public List<User> getByDeptIds(Collection<String> deptIds) {
        if (CollectionUtils.isEmpty(deptIds)) {
            return Lists.newArrayList();
        }
        QueryBuilder queryBuilder = SQL.query(User.class).in(User::getDeptId, deptIds);
        return sqlExecutor.queryList(queryBuilder);
    }

    /** 查询租户下全部用户（租户禁用/到期时批量失效令牌用）。 */
    public List<User> queryByTenantCode(String tenantCode) {
        return sqlExecutor.queryList(SQL.query(User.class).eq(User::getTenantCode, tenantCode));
    }

    /**
     * 查询所有平台管理员（is_admin=1）。
     */
    public List<User> queryAdmins() {
        QueryBuilder queryBuilder = SQL.query(User.class)
                .eq(User::getIsAdmin, true);
        return sqlExecutor.queryList(queryBuilder);
    }

    /**
     * 按部门层级编码前缀查询用户（含子部门所有用户）
     * 通过 JOIN sys_dept 实现：u.dept_id = d.id AND d.dept_code LIKE 'prefix%'
     * <p>
     * 该查询需跨表 JOIN，无法用 QueryBuilder 表达，故使用参数化原生 SQL；
     * 原生 SQL 不经过 ORM 权限策略，因此此处<b>显式追加租户隔离条件</b>，
     * 避免 LIKE 前缀跨租户命中同前缀部门的用户。
     */
    public List<User> getByDeptCodePrefix(String deptCodePrefix) {
        if (deptCodePrefix == null || deptCodePrefix.isEmpty()) {
            return Lists.newArrayList();
        }
        List<String> tenants = TenantUtil.currentTenantCodesForIsolation();
        if (tenants != null && tenants.isEmpty()) {
            return Lists.newArrayList();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT u.* FROM sys_user u INNER JOIN sys_dept d ON u.dept_id = d.id "
                        + "WHERE d.dept_code LIKE ? AND u.is_deleted = 0 AND d.is_deleted = 0");
        List<Object> params = new ArrayList<>();
        params.add(deptCodePrefix + "%");
        if (tenants != null) {
            String placeholders = String.join(", ", Collections.nCopies(tenants.size(), "?"));
            sql.append(" AND u.tenant_code IN (").append(placeholders).append(')');
            sql.append(" AND d.tenant_code IN (").append(placeholders).append(')');
            params.addAll(tenants);
            params.addAll(tenants);
        }
        return sqlExecutor.queryList(sql.toString(), User.class, params.toArray());
    }

    public void updateById(User user) {
        sqlExecutor.updateById(user);
    }

    /**
     * 按工号更新用户状态（禁用/启用专用）
     */
    public void updateStatusByEmployeeId(String employeeId, String status) {
        UpdateBuilder updateBuilder = SQL.update(User.class).ignoreInvalid(true)
                .set(User::getStatus, status)
                .eq(User::getEmployeeId, employeeId);
        sqlExecutor.update(updateBuilder);
    }

    /**
     * 按工号设置平台管理员标志（超管任命/撤销管理员专用）。
     */
    public void updateIsAdminByEmployeeId(String employeeId, boolean isAdmin) {
        UpdateBuilder updateBuilder = SQL.update(User.class).ignoreInvalid(true)
                .set(User::getIsAdmin, isAdmin)
                .eq(User::getEmployeeId, employeeId);
        sqlExecutor.update(updateBuilder);
    }

    /**
     * 自增 Token 版本号（强制下线/修改密码时调用）。
     * <p>
     * 注：自增表达式无法通过 UpdateBuilder.set 实现，使用原生 SQL。
     * BaseEntity 的 LogicFieldStrategy 仅对 Builder API 生效，此处手动追加 is_deleted 条件。
     * 此处不追加租户条件：employee_id 全局唯一，且调用方（禁用用户/重置密码/强制下线）
     * 均已在 Service 层完成租户归属校验，追加租户条件反而可能导致撤销动作静默失效。
     *
     * @param employeeId 工号
     */
    public void incrementTokenVersion(String employeeId) {
        String sql = "UPDATE sys_user SET token_version = IFNULL(token_version, 0) + 1 WHERE employee_id = ? AND is_deleted = 0";
        sqlExecutor.getJdbcTemplate().update(sql, employeeId);
    }

    /**
     * 获取用户的 Token 版本号。
     *
     * @param employeeId 工号
     * @return token_version，不存在时返回 0
     */
    public int getTokenVersion(String employeeId) {
        QueryBuilder queryBuilder = SQL.query(User.class)
                .withColumn(User::getTokenVersion)
                .eq(User::getEmployeeId, employeeId);
        User user = sqlExecutor.queryFirst(queryBuilder);
        if (user == null || user.getTokenVersion() == null) {
            return 0;
        }
        return user.getTokenVersion();
    }

}
