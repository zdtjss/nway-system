package com.nway.system.identity.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.spring.jdbc.sql.builder.UpdateBuilder;
import com.nway.system.identity.entity.UserLoginKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class UserLoginKeyDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public UserLoginKey getByLoginName(String loginName) {
        QueryBuilder queryBuilder = SQL.query(UserLoginKey.class)
                .eq(UserLoginKey::getLoginName, loginName)
                .eq(UserLoginKey::getIsValid, true);
        return sqlExecutor.queryFirst(queryBuilder);
    }

    /**
     * 按工号查询登录凭证（含有效性标记，不限定 isValid）。
     */
    public UserLoginKey getByEmployeeId(String employeeId) {
        QueryBuilder queryBuilder = SQL.query(UserLoginKey.class)
                .eq(UserLoginKey::getEmployeeId, employeeId);
        return sqlExecutor.queryFirst(queryBuilder);
    }

    public UserLoginKey getByBioMobile(String bioMobile) {
        QueryBuilder queryBuilder = SQL.query(UserLoginKey.class)
                .eq(UserLoginKey::getBioMobile, bioMobile)
                .eq(UserLoginKey::getIsValid, true);
        return sqlExecutor.queryFirst(queryBuilder);
    }

    public UserLoginKey getByBioPc(String bioPc) {
        QueryBuilder queryBuilder = SQL.query(UserLoginKey.class)
                .eq(UserLoginKey::getBioPc, bioPc)
                .eq(UserLoginKey::getIsValid, true);
        return sqlExecutor.queryFirst(queryBuilder);
    }

    /**
     * 按工号更新登录凭证的租户编码（用户跨租户迁移专用）。
     * <p>
     * 登录凭证按 employee_id 全局唯一，调用方已完成用户迁移的租户授权校验；
     * 若不随用户一起迁移，改密/禁用等按当前租户过滤的更新会命中 0 行。
     */
    public void updateTenantCodeByEmployeeId(String employeeId, String tenantCode) {
        UpdateBuilder updateBuilder = SQL.update(UserLoginKey.class).ignoreInvalid(true)
                .set(UserLoginKey::getTenantCode, tenantCode)
                .eq(UserLoginKey::getEmployeeId, employeeId);
        sqlExecutor.update(updateBuilder);
    }

    public int updateByEmployeeId(UserLoginKey loginKey) {
        UpdateBuilder updateBuilder = SQL.update(UserLoginKey.class).ignoreInvalid(true);
        updateBuilder.set(UserLoginKey::getPassword, loginKey.getPassword());
        updateBuilder.set(UserLoginKey::getBioPc, loginKey.getBioPc());
        updateBuilder.set(UserLoginKey::getBioMobile, loginKey.getBioMobile());
        updateBuilder.set(UserLoginKey::getPwdResetRequired, loginKey.getPwdResetRequired());
        updateBuilder.eq(UserLoginKey::getEmployeeId, loginKey.getEmployeeId());
        return sqlExecutor.update(updateBuilder);
    }

    public void insert(UserLoginKey loginKey) {
        sqlExecutor.insert(loginKey);
    }

    public void disableByEmployeeId(String employeeId) {
        UpdateBuilder updateBuilder = SQL.update(UserLoginKey.class).ignoreInvalid(true)
                .set(UserLoginKey::getIsValid, false)
                .eq(UserLoginKey::getEmployeeId, employeeId);
        sqlExecutor.update(updateBuilder);
    }

    /**
     * 按工号恢复登录键有效（启用用户专用）
     */
    public void enableByEmployeeId(String employeeId) {
        UpdateBuilder updateBuilder = SQL.update(UserLoginKey.class).ignoreInvalid(true)
                .set(UserLoginKey::getIsValid, true)
                .eq(UserLoginKey::getEmployeeId, employeeId);
        sqlExecutor.update(updateBuilder);
    }

}
