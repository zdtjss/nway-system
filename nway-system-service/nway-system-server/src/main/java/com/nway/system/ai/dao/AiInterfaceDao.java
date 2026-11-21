package com.nway.system.ai.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.ai.entity.AiInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 接口元数据 DAO。
 * <p>
 * 实体继承 {@code BaseEntity}，查询/更新/删除自动追加租户隔离（读取含 public 共享租户）。
 *
 * @author nway
 */
@Repository
public class AiInterfaceDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(AiInterface entity) {
        sqlExecutor.insert(entity);
    }

    public void updateById(AiInterface entity) {
        sqlExecutor.updateById(entity);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, AiInterface.class);
    }

    public AiInterface queryById(String id) {
        return sqlExecutor.queryById(id, AiInterface.class);
    }

    public AiInterface queryByCode(String interfaceCode) {
        QueryBuilder qb = SQL.query(AiInterface.class).eq(AiInterface::getInterfaceCode, interfaceCode);
        return sqlExecutor.queryFirst(qb);
    }

    public List<AiInterface> queryByStatus(String status) {
        QueryBuilder qb = SQL.query(AiInterface.class)
                .eq(AiInterface::getStatus, status)
                .orderByDesc(AiInterface::getModifiedTime);
        return sqlExecutor.queryList(qb);
    }

    public List<AiInterface> queryAll() {
        QueryBuilder qb = SQL.query(AiInterface.class).orderByDesc(AiInterface::getModifiedTime);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按租户集合查询已发布接口（系统上下文调用，租户条件显式给出）。
     */
    public List<AiInterface> queryPublishedInTenants(java.util.Collection<String> tenantCodes) {
        QueryBuilder qb = SQL.query(AiInterface.class)
                .eq(AiInterface::getStatus, "PUBLISHED")
                .in(AiInterface::getTenantCode, tenantCodes)
                .orderByDesc(AiInterface::getModifiedTime);
        return sqlExecutor.queryList(qb);
    }
}
