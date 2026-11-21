package com.nway.system.ai.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.ai.entity.AiPrompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 提示词 DAO。
 *
 * @author nway
 */
@Repository
public class AiPromptDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(AiPrompt entity) {
        sqlExecutor.insert(entity);
    }

    public void updateById(AiPrompt entity) {
        sqlExecutor.updateById(entity);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, AiPrompt.class);
    }

    public AiPrompt queryById(String id) {
        return sqlExecutor.queryById(id, AiPrompt.class);
    }

    /**
     * 查询当前租户可见的启用提示词。
     * <p>
     * 范围过滤（SYSTEM/ROLE/USER）在服务层完成，条数很少，避免为 OR 组合引入额外 SQL 复杂度。
     */
    public List<AiPrompt> queryActive() {
        QueryBuilder qb = SQL.query(AiPrompt.class)
                .eq(AiPrompt::getStatus, "ACTIVE")
                .orderByDesc(AiPrompt::getPriority)
                .andOrderByDesc(AiPrompt::getModifiedTime);
        return sqlExecutor.queryList(qb);
    }

    public List<AiPrompt> queryAll() {
        QueryBuilder qb = SQL.query(AiPrompt.class)
                .orderByDesc(AiPrompt::getModifiedTime);
        return sqlExecutor.queryList(qb);
    }
}
