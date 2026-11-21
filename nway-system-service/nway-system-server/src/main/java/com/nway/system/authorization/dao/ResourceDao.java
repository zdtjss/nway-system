package com.nway.system.authorization.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.DeleteBuilder;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.authorization.entity.SysResource;
import com.nway.system.authorization.entity.SysResourceAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 资源目录 DAO。
 * <p>
 * 资源是跨租户元数据（PLATFORM/SHARED 对所有租户可见），因此不继承 BaseEntity，
 * 不由租户隔离策略自动过滤；可见性由 {@code tenant_scope} 在业务层显式判定。
 */
@Repository
public class ResourceDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    // ====== 资源 ======

    public void insert(SysResource resource) {
        sqlExecutor.insert(resource);
    }

    public void updateById(SysResource resource) {
        sqlExecutor.updateById(resource);
    }

    public SysResource getById(String id) {
        return sqlExecutor.queryById(id, SysResource.class);
    }

    public SysResource getByAppAndCode(String appCode, String resourceCode) {
        QueryBuilder qb = SQL.query(SysResource.class)
                .eq(SysResource::getAppCode, appCode)
                .eq(SysResource::getResourceCode, resourceCode);
        return sqlExecutor.queryOne(qb);
    }

    public boolean exists(String appCode, String resourceCode) {
        return getByAppAndCode(appCode, resourceCode) != null;
    }

    public List<SysResource> listAll() {
        return sqlExecutor.queryList(SQL.query(SysResource.class)
                .orderBy(SysResource::getResourceCode));
    }

    public List<SysResource> listByApp(String appCode) {
        return sqlExecutor.queryList(SQL.query(SysResource.class)
                .eq(SysResource::getAppCode, appCode)
                .orderBy(SysResource::getResourceCode));
    }

    public List<SysResource> listByApps(Collection<String> appCodes) {
        if (CollectionUtils.isEmpty(appCodes)) {
            return Collections.emptyList();
        }
        return sqlExecutor.queryList(SQL.query(SysResource.class)
                .in(SysResource::getAppCode, appCodes)
                .orderBy(SysResource::getResourceCode));
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysResource.class);
    }

    // ====== 动作 ======

    public void insertAction(SysResourceAction action) {
        sqlExecutor.insert(action);
    }

    public void batchInsertActions(List<SysResourceAction> actions) {
        if (CollectionUtils.isEmpty(actions)) {
            return;
        }
        sqlExecutor.batchInsert(actions);
    }

    public List<SysResourceAction> listActions(String resourceId) {
        return sqlExecutor.queryList(SQL.query(SysResourceAction.class)
                .eq(SysResourceAction::getResourceId, resourceId)
                .orderBy(SysResourceAction::getSort));
    }

    public List<SysResourceAction> listActions(Collection<String> resourceIds) {
        if (CollectionUtils.isEmpty(resourceIds)) {
            return Collections.emptyList();
        }
        return sqlExecutor.queryList(SQL.query(SysResourceAction.class)
                .in(SysResourceAction::getResourceId, resourceIds)
                .orderBy(SysResourceAction::getSort));
    }

    public SysResourceAction getAction(String resourceId, String action) {
        return sqlExecutor.queryOne(SQL.query(SysResourceAction.class)
                .eq(SysResourceAction::getResourceId, resourceId)
                .eq(SysResourceAction::getAction, action));
    }

    /** 替换式保存：清空资源下的旧动作后写入新动作。 */
    public void deleteActions(String resourceId) {
        DeleteBuilder db = SQL.delete(SysResourceAction.class)
                .eq(SysResourceAction::getResourceId, resourceId);
        sqlExecutor.delete(db);
    }

    /** 删除单个动作（资源动作下线用）。 */
    public void deleteAction(String resourceId, String action) {
        DeleteBuilder db = SQL.delete(SysResourceAction.class)
                .eq(SysResourceAction::getResourceId, resourceId)
                .eq(SysResourceAction::getAction, action);
        sqlExecutor.delete(db);
    }
}
