package com.nway.system.audit.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.audit.entity.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志 DAO。
 *
 * @author nway
 */
@Repository
public class AuditLogDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    /** 写入一条审计日志（由 AuditLogAspect 调用；失败不影响业务）。 */
    public void insert(AuditLog log) {
        sqlExecutor.insert(log);
    }

    /**
     * 分页查询审计日志，按创建时间倒序。
     */
    public Page<AuditLog> queryPage(String operatorId, String operationType, String apiPath,
                                    LocalDateTime startTime, LocalDateTime endTime, int page, int limit) {
        QueryBuilder queryBuilder = buildQuery(operatorId, operationType, apiPath, startTime, endTime)
                .orderByDesc(AuditLog::getCreateTime);
        return sqlExecutor.queryPage(queryBuilder, page, limit);
    }

    /**
     * 统计符合查询条件的审计日志数量。
     */
    public int count(String operatorId, String operationType, String apiPath,
                     LocalDateTime startTime, LocalDateTime endTime) {
        QueryBuilder queryBuilder = buildQuery(operatorId, operationType, apiPath, startTime, endTime);
        return sqlExecutor.count(queryBuilder);
    }

    private QueryBuilder buildQuery(String operatorId, String operationType, String apiPath,
                                    LocalDateTime startTime, LocalDateTime endTime) {
        QueryBuilder qb = SQL.query(AuditLog.class).ignoreInvalidDeep(true)
                .eq(AuditLog::getOperatorId, operatorId)
                .eq(AuditLog::getOperationType, operationType)
                .like(AuditLog::getApiPath, apiPath)
                .ge(AuditLog::getCreateTime, startTime)
                .le(AuditLog::getCreateTime, endTime);
        // 审计日志实体不继承 BaseEntity，无 ORM 租户策略，须在此显式补租户隔离。
        // 语义与 TenantPermissionStrategy 一致：超管全域；其余按读取合（含 public）；无上下文 fail-closed。
        if (LoginUserContext.current() == null) {
            qb.eq(AuditLog::getTenantCode, DENY);
        } else {
            List<String> tenants = TenantUtil.currentTenantCodesForIsolation();
            if (tenants != null) {
                if (tenants.isEmpty()) {
                    qb.eq(AuditLog::getTenantCode, DENY);
                } else if (tenants.size() == 1) {
                    qb.eq(AuditLog::getTenantCode, tenants.get(0));
                } else {
                    qb.in(AuditLog::getTenantCode, tenants);
                }
            }
        }
        return qb;
    }

    /** 恒假哨兵值：用于"无上下文/无租户"时强制查不到任何行。 */
    static final String DENY = "__DENY__";
}
