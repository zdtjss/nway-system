package com.nway.system.message.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.message.entity.MessageTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageTargetDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insertBatch(List<MessageTarget> targetList) {
        sqlExecutor.batchInsert(targetList);
    }

    public List<MessageTarget> selectList(QueryBuilder queryBuilder) {
        return sqlExecutor.queryList(queryBuilder);
    }

    public List<MessageTarget> queryByMessageId(String messageId) {
        return sqlExecutor.queryList(SQL.query(MessageTarget.class).eq(MessageTarget::getMessageId, messageId));
    }

    public List<MessageTarget> selectBatchIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return sqlExecutor.queryList(SQL.query(MessageTarget.class).in(MessageTarget::getId, ids));
    }
}
