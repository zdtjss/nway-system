package com.nway.system.ai.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.ai.entity.AiConversation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * AI 会话索引 DAO。
 *
 * @author nway
 */
@Repository
public class AiConversationDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(AiConversation entity) {
        sqlExecutor.insert(entity);
    }

    public void updateById(AiConversation entity) {
        sqlExecutor.updateById(entity);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, AiConversation.class);
    }

    public AiConversation queryById(String id) {
        return sqlExecutor.queryById(id, AiConversation.class);
    }

    public Page<AiConversation> queryPageByUser(String userId, String keyword, int current, int size) {
        QueryBuilder qb = SQL.query(AiConversation.class)
                .eq(AiConversation::getUserId, userId)
                .ne(AiConversation::getStatus, "DELETED");
        if (StringUtils.isNotBlank(keyword)) {
            qb.like(AiConversation::getTitle, "%" + keyword.trim() + "%");
        }
        qb.orderByDesc(AiConversation::getLastMessageAt)
                .andOrderByDesc(AiConversation::getCreateTime);
        return sqlExecutor.queryPage(qb, current, size);
    }
}
