package com.nway.system.authorization.dao;

import com.nway.system.authorization.entity.SysGrantChange;
import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.DeleteBuilder;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.spring.jdbc.sql.builder.UpdateBuilder;
import com.nway.system.authorization.entity.SysGrant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 统一授权 DAO。
 */
@Repository
public class GrantDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    /**
     * 直接插入授权。<b>不处理墓碑行</b>：业务写入请用 {@link #insertOrRevive(SysGrant)}，
     * 否则同键已逻辑删除时会撞 uidx_grant_subject_resource（D5）。
     */
    public void insert(SysGrant grant) {
        sqlExecutor.insert(grant);
    }

    /**
     * 插入授权；同一唯一键已存在<b>逻辑删除</b>的墓碑行时复用该行（复活）。
     * <p>
     * {@code uidx_grant_subject_resource} 未包含 {@code is_deleted}：逻辑删除的行仍占用
     * (subject_type, subject_id, app_code, resource_code, effect) 唯一键。直接 INSERT 会在
     * “撤销后重新授权 / 替换式保存”时抛 Duplicate entry（D5）。
     * <p>
     * 复活保留原 id（变更留痕前后可比对），version 在旧值上递增。
     */
    public void insertOrRevive(SysGrant grant) {
        SysGrant tombstone = findTombstoneByKey(grant.getSubjectType(), grant.getSubjectId(),
                grant.getAppCode(), grant.getResourceCode(), grant.getEffect());
        if (tombstone == null) {
            sqlExecutor.insert(grant);
            return;
        }
        grant.setId(tombstone.getId());
        grant.setIsDeleted(Boolean.FALSE);
        grant.setVersion(tombstone.getVersion() == null ? 2L : tombstone.getVersion() + 1);
        // 不能用 updateById：其 WHERE 会叠加 LogicFieldStrategy 的 is_deleted=0，永远更新不到墓碑行。
        // UpdateBuilder 的 initFilled 会在 SET 中自动置 is_deleted=false（LogicFieldStrategy），
        // 此处只需显式绕过 WHERE 策略。
        UpdateBuilder ub = SQL.update(SysGrant.class)
                .set(SysGrant::getActions, grant.getActions())
                .set(SysGrant::getDataScope, grant.getDataScope())
                .set(SysGrant::getScopeDeptIds, grant.getScopeDeptIds())
                .set(SysGrant::getEffectiveFrom, grant.getEffectiveFrom())
                .set(SysGrant::getExpireAt, grant.getExpireAt())
                .set(SysGrant::getStatus, grant.getStatus())
                .set(SysGrant::getVersion, grant.getVersion())
                .set(SysGrant::getTenantCode, grant.getTenantCode())
                .eq(SysGrant::getId, tombstone.getId());
        ub.ignorePermission();
        sqlExecutor.update(ub);
    }

    /**
     * 按唯一键查已逻辑删除的墓碑行。
     * <p>
     * 必须绕过行级策略（{@code ignorePermission}）：LogicFieldStrategy 会自动追加
     * {@code is_deleted=0}，叠加后永远查不到墓碑行。
     */
    private SysGrant findTombstoneByKey(String subjectType, String subjectId, String appCode,
                                        String resourceCode, String effect) {
        QueryBuilder qb = SQL.query(SysGrant.class)
                .eq(SysGrant::getSubjectType, subjectType)
                .eq(SysGrant::getSubjectId, subjectId)
                .eq(SysGrant::getAppCode, appCode)
                .eq(SysGrant::getResourceCode, resourceCode)
                .eq(SysGrant::getEffect, effect)
                .eq(SysGrant::getIsDeleted, true);
        qb.ignorePermission();
        return sqlExecutor.queryOne(qb);
    }

    public void batchInsert(List<SysGrant> grants) {
        if (CollectionUtils.isEmpty(grants)) {
            return;
        }
        // 逐个走复活逻辑：批量 INSERT 会绕过墓碑行检测，同键遗留行会撞唯一键（D5）
        grants.forEach(this::insertOrRevive);
    }

    public void updateById(SysGrant grant) {
        sqlExecutor.updateById(grant);
    }

    public SysGrant getById(String id) {
        return sqlExecutor.queryById(id, SysGrant.class);
    }

    public List<SysGrant> queryBySubject(String subjectType, String subjectId) {
        return sqlExecutor.queryList(SQL.query(SysGrant.class)
                .eq(SysGrant::getSubjectType, subjectType)
                .eq(SysGrant::getSubjectId, subjectId));
    }

    public List<SysGrant> queryBySubjects(String subjectType, Collection<String> subjectIds) {
        if (CollectionUtils.isEmpty(subjectIds)) {
            return Collections.emptyList();
        }
        return sqlExecutor.queryList(SQL.query(SysGrant.class)
                .eq(SysGrant::getSubjectType, subjectType)
                .in(SysGrant::getSubjectId, subjectIds));
    }

    public List<SysGrant> queryByRole(String roleId) {
        return queryBySubject("ROLE", roleId);
    }

    /** 按唯一键查找（subject+resource+effect），用于 upsert。 */
    public SysGrant findByKey(String subjectType, String subjectId, String appCode,
                              String resourceCode, String effect) {
        return sqlExecutor.queryOne(SQL.query(SysGrant.class)
                .eq(SysGrant::getSubjectType, subjectType)
                .eq(SysGrant::getSubjectId, subjectId)
                .eq(SysGrant::getAppCode, appCode)
                .eq(SysGrant::getResourceCode, resourceCode)
                .eq(SysGrant::getEffect, effect));
    }

    public List<SysGrant> queryByUser(String employeeId) {
        return queryBySubject("USER", employeeId);
    }

    /** 某应用某资源的全部授权（资源反查/影响面分析）。 */
    public List<SysGrant> queryByResource(String appCode, String resourceCode) {
        return sqlExecutor.queryList(SQL.query(SysGrant.class)
                .eq(SysGrant::getAppCode, appCode)
                .eq(SysGrant::getResourceCode, resourceCode));
    }

    /** 某应用多个资源的授权（列表页批量统计，避免逐接口查询）。 */
    public List<SysGrant> queryByResources(String appCode, Collection<String> resourceCodes) {
        if (CollectionUtils.isEmpty(resourceCodes)) {
            return Collections.emptyList();
        }
        return sqlExecutor.queryList(SQL.query(SysGrant.class)
                .eq(SysGrant::getAppCode, appCode)
                .in(SysGrant::getResourceCode, resourceCodes));
    }

    /** 删除某应用某资源的全部授权（资源下线/替换式保存）。 */
    public void deleteByResource(String appCode, String resourceCode) {
        DeleteBuilder db = SQL.delete(SysGrant.class)
                .eq(SysGrant::getAppCode, appCode)
                .eq(SysGrant::getResourceCode, resourceCode);
        sqlExecutor.delete(db);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysGrant.class);
    }

    public void deleteBySubject(String subjectType, String subjectId) {
        DeleteBuilder db = SQL.delete(SysGrant.class)
                .eq(SysGrant::getSubjectType, subjectType)
                .eq(SysGrant::getSubjectId, subjectId);
        sqlExecutor.delete(db);
    }

    public void deleteByRole(String roleId) {
        deleteBySubject("ROLE", roleId);
    }

    public void deleteBySubjectAndResource(String subjectType, String subjectId,
                                           String appCode, String resourceCode) {
        DeleteBuilder db = SQL.delete(SysGrant.class)
                .eq(SysGrant::getSubjectType, subjectType)
                .eq(SysGrant::getSubjectId, subjectId)
                .eq(SysGrant::getAppCode, appCode)
                .eq(SysGrant::getResourceCode, resourceCode);
        sqlExecutor.delete(db);
    }

    /** 授权变更留痕（同事务写入）。 */
    public void insertChange(com.nway.system.authorization.entity.SysGrantChange change) {
        sqlExecutor.insert(change);
    }

    public com.nway.system.authorization.entity.SysGrantChange getChange(String id) {
        return sqlExecutor.queryById(id, com.nway.system.authorization.entity.SysGrantChange.class);
    }

    public List<com.nway.system.authorization.entity.SysGrantChange> queryChanges(String targetType,
                                                                              String targetId,
                                                                              int limit) {
        QueryBuilder qb = SQL.query(com.nway.system.authorization.entity.SysGrantChange.class)
                .eq(com.nway.system.authorization.entity.SysGrantChange::getTargetType, targetType)
                .eq(com.nway.system.authorization.entity.SysGrantChange::getTargetId, targetId)
                .orderByDesc(com.nway.system.authorization.entity.SysGrantChange::getCreateTime);
        List<com.nway.system.authorization.entity.SysGrantChange> all = sqlExecutor.queryList(qb);
        return all.size() <= limit ? all : all.subList(0, limit);
    }
}
