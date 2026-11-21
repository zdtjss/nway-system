package com.nway.system.tenant.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.DeleteBuilder;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.tenant.entity.AdminTenant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 管理员授权 DAO：维护管理员可管理的授权租户。
 */
@Repository
public class AdminGrantDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insertTenant(AdminTenant tenant) {
        sqlExecutor.insert(tenant);
    }

    public void deleteTenantByEmployee(String employeeId) {
        DeleteBuilder builder = SQL.delete(AdminTenant.class)
                .eq(AdminTenant::getEmployeeId, employeeId);
        sqlExecutor.delete(builder);
    }

    public List<String> queryTenantCodes(String employeeId) {
        QueryBuilder builder = SQL.query(AdminTenant.class)
                .withColumn(AdminTenant::getTenantCode)
                .eq(AdminTenant::getEmployeeId, employeeId);
        List<AdminTenant> list = sqlExecutor.queryList(builder);
        return list.stream().map(AdminTenant::getTenantCode).toList();
    }
}
