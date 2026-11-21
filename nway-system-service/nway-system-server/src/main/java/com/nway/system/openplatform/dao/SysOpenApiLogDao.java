package com.nway.system.openplatform.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.openplatform.entity.SysOpenApiLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 开放平台调用日志 DAO。
 * <p>
 * 实体不继承 BaseEntity，无 ORM 租户策略，查询须显式补租户隔离
 * （超管全域；其余按读取合含 public；无上下文 fail-closed）。
 *
 * @author nway
 */
@Repository
public class SysOpenApiLogDao {

    /** 恒假哨兵值：用于"无上下文/无租户"时强制查不到任何行。 */
    private static final String DENY = "__DENY__";

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(SysOpenApiLog log) {
        sqlExecutor.insert(log);
    }

    /**
     * 按应用查询调用日志
     */
    public List<SysOpenApiLog> queryByApp(String appCode) {
        QueryBuilder qb = SQL.query(SysOpenApiLog.class)
                .eq(SysOpenApiLog::getAppCode, appCode)
                .orderByDesc(SysOpenApiLog::getCreateTime);
        applyTenantScope(qb);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询全部调用日志
     */
    public List<SysOpenApiLog> queryAll() {
        QueryBuilder qb = SQL.query(SysOpenApiLog.class)
                .orderByDesc(SysOpenApiLog::getCreateTime);
        applyTenantScope(qb);
        return sqlExecutor.queryList(qb);
    }

    private void applyTenantScope(QueryBuilder qb) {
        if (LoginUserContext.current() == null) {
            qb.eq(SysOpenApiLog::getTenantCode, DENY);
            return;
        }
        List<String> tenants = TenantUtil.currentTenantCodesForIsolation();
        if (tenants == null) {
            return;
        }
        if (tenants.isEmpty()) {
            qb.eq(SysOpenApiLog::getTenantCode, DENY);
        } else if (tenants.size() == 1) {
            qb.eq(SysOpenApiLog::getTenantCode, tenants.get(0));
        } else {
            qb.in(SysOpenApiLog::getTenantCode, tenants);
        }
    }
}