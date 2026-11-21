package com.nway.system.portal.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.portal.entity.App;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 应用 DAO
 */
@Repository
public class AppDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(App app) {
        sqlExecutor.insert(app);
    }

    public void updateById(App app) {
        sqlExecutor.updateById(app);
    }

    public App getById(String id) {
        return sqlExecutor.queryById(id, App.class);
    }

    public App getByCode(String appCode) {
        QueryBuilder qb = SQL.query(App.class).eq(App::getAppCode, appCode);
        return sqlExecutor.queryFirst(qb);
    }

    public List<App> queryAll() {
        QueryBuilder qb = SQL.query(App.class).orderBy(App::getSort);
        return sqlExecutor.queryList(qb);
    }

    public List<App> queryByStatus(String status) {
        QueryBuilder qb = SQL.query(App.class)
                .eq(App::getStatus, status)
                .orderBy(App::getSort);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按状态 + 归属租户集合查询应用（策略配置弹窗用：只展示目标角色租户 + public 共享租户的应用）
     */
    public List<App> queryByStatusInTenants(String status, Collection<String> tenantCodes) {
        if (CollectionUtils.isEmpty(tenantCodes)) {
            return Collections.emptyList();
        }
        QueryBuilder qb = SQL.query(App.class)
                .eq(App::getStatus, status)
                .in(App::getTenantCode, tenantCodes)
                .orderBy(App::getSort);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按 appCode 集合批量查询，返回 appCode → App 映射
     */
    public Map<String, App> queryByCodes(Collection<String> appCodes) {
        if (CollectionUtils.isEmpty(appCodes)) {
            return Collections.emptyMap();
        }
        List<App> list = sqlExecutor.queryList(
                SQL.query(App.class).in(App::getAppCode, appCodes));
        return list.stream().collect(Collectors.toMap(App::getAppCode, a -> a, (a, b) -> a));
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, App.class);
    }
}
