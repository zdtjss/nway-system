package com.nway.system.tenant.dao;

import com.google.common.collect.Lists;
import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.tenant.web.model.TenantQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

@Repository
public class TenantDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public Page<Tenant> queryPage(TenantQuery query) {
        QueryBuilder queryBuilder = SQL.query(Tenant.class).ignoreInvalidDeep(true)
                .like(Tenant::getTenantName, query.getTenantName())
                .eq(Tenant::getTenantCode, query.getTenantCode())
                .eq(Tenant::getStatus, query.getStatus());
        return sqlExecutor.queryPage(queryBuilder, query.getCurrent(), query.getSize());
    }

    public Tenant getById(String id) {
        return sqlExecutor.queryById(id, Tenant.class);
    }

    public List<Tenant> queryByIds(Collection<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Lists.newArrayList();
        }
        return sqlExecutor.queryList(ids, Tenant.class);
    }

    public List<Tenant> queryByTenantCode(Collection<String> tenantCodes) {
        if (CollectionUtils.isEmpty(tenantCodes)) {
            return Lists.newArrayList();
        }
        QueryBuilder queryBuilder = SQL.query(Tenant.class).in(Tenant::getTenantCode, tenantCodes);
        return sqlExecutor.queryList(queryBuilder);
    }

    public Tenant getByCode(String tenantCode) {
        return sqlExecutor.queryFirst(SQL.query(Tenant.class).eq(Tenant::getTenantCode, tenantCode));
    }

    /**
     * 校验租户编码是否已存在（排除自身）。
     * <p>
     * 查询自动过滤已删除记录，因此禁用/过期等任意状态的未删除租户均参与唯一性校验。
     */
    public int countByCode(String tenantCode, String excludeId) {
        QueryBuilder queryBuilder = SQL.query(Tenant.class).eq(Tenant::getTenantCode, tenantCode);
        if (StringUtils.hasText(excludeId)) {
            queryBuilder.ne(Tenant::getId, excludeId);
        }
        return sqlExecutor.count(queryBuilder);
    }

    /**
     * 校验租户名称是否已存在（排除自身）。
     * <p>
     * 查询自动过滤已删除记录，因此禁用/过期等任意状态的未删除租户均参与唯一性校验。
     */
    public int countByName(String tenantName, String excludeId) {
        QueryBuilder queryBuilder = SQL.query(Tenant.class).eq(Tenant::getTenantName, tenantName);
        if (StringUtils.hasText(excludeId)) {
            queryBuilder.ne(Tenant::getId, excludeId);
        }
        return sqlExecutor.count(queryBuilder);
    }

    public void insert(Tenant tenant) {
        sqlExecutor.insert(tenant);
    }

    public void updateById(Tenant tenant) {
        sqlExecutor.updateById(tenant);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, Tenant.class);
    }

    public List<Tenant> queryAll() {
        return sqlExecutor.queryList(SQL.query(Tenant.class).eq(Tenant::getStatus, "using"));
    }
}
