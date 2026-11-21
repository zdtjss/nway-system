package com.nway.system.ai.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.ai.entity.AiModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 模型 Provider DAO。
 *
 * @author nway
 */
@Repository
public class AiModelDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(AiModel entity) {
        sqlExecutor.insert(entity);
    }

    public void updateById(AiModel entity) {
        sqlExecutor.updateById(entity);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, AiModel.class);
    }

    public AiModel queryById(String id) {
        return sqlExecutor.queryById(id, AiModel.class);
    }

    public List<AiModel> queryAll() {
        return sqlExecutor.queryList(SQL.query(AiModel.class).orderByDesc(AiModel::getIsDefault));
    }

    /**
     * 查询当前租户可见的默认 Provider（按 is_default desc、modified_time desc 取第一条）。
     */
    public AiModel queryDefault() {
        QueryBuilder qb = SQL.query(AiModel.class)
                .eq(AiModel::getStatus, "ACTIVE")
                .orderByDesc(AiModel::getIsDefault)
                .andOrderByDesc(AiModel::getModifiedTime);
        return sqlExecutor.queryFirst(qb);
    }

    public AiModel queryDefaultInTenants(java.util.Collection<String> tenantCodes) {
        QueryBuilder qb = SQL.query(AiModel.class)
                .eq(AiModel::getStatus, "ACTIVE")
                .in(AiModel::getTenantCode, tenantCodes)
                .orderByDesc(AiModel::getIsDefault)
                .andOrderByDesc(AiModel::getModifiedTime);
        return sqlExecutor.queryFirst(qb);
    }
}
