package com.nway.system.identity.dao;

import com.google.common.collect.Lists;
import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.identity.web.model.RoleQuery;
import com.nway.system.identity.entity.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

/**
 * 角色 DAO
 */
@Repository
public class RoleDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(Role role) {
        sqlExecutor.insert(role);
    }

    public Role queryById(String id) {
        return sqlExecutor.queryById(id, Role.class);
    }

    public List<Role> queryByIds(Collection<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Lists.newArrayList();
        }
        return sqlExecutor.queryList(ids, Role.class);
    }

    /**
     * 按角色编码批量查询
     */
    public List<Role> queryByCodes(Collection<String> roleCodes) {
        if (CollectionUtils.isEmpty(roleCodes)) {
            return Lists.newArrayList();
        }
        QueryBuilder qb = SQL.query(Role.class).in(Role::getRoleCode, roleCodes);
        return sqlExecutor.queryList(qb);
    }

    public void updateById(Role role) {
        sqlExecutor.updateById(role);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, Role.class);
    }

    public Page<Role> queryPage(RoleQuery roleQuery) {
        QueryBuilder qb = SQL.query(Role.class).ignoreInvalidDeep(true)
                .eq(Role::getAppCode, roleQuery.getAppCode())
                .like(Role::getRoleName, roleQuery.getRoleName());
        return sqlExecutor.queryPage(qb, roleQuery.getCurrent(), roleQuery.getSize());
    }

    /**
     * 按应用查询角色
     */
    public List<Role> queryByApp(String appCode) {
        QueryBuilder qb = SQL.query(Role.class).eq(Role::getAppCode, appCode);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询指定租户下的所有角色
     */
    public List<Role> queryByTenantCode(String tenantCode) {
        QueryBuilder qb = SQL.query(Role.class).eq(Role::getTenantCode, tenantCode);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询指定租户集合下的所有角色（如：本租户 + public 共享租户）
     */
    public List<Role> queryByTenantCodes(Collection<String> tenantCodes) {
        if (CollectionUtils.isEmpty(tenantCodes)) {
            return Lists.newArrayList();
        }
        QueryBuilder qb = SQL.query(Role.class).in(Role::getTenantCode, tenantCodes);
        return sqlExecutor.queryList(qb);
    }
}
