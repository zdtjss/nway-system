package com.nway.system.ai.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.DeleteBuilder;
import com.nway.system.ai.entity.AiMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 会话消息 DAO。
 *
 * @author nway
 */
@Repository
public class AiMessageDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(AiMessage entity) {
        sqlExecutor.insert(entity);
    }

    /** 按会话读取消息，时间升序（会话内顺序）。 */
    public List<AiMessage> queryByConversation(String conversationId) {
        return sqlExecutor.queryList(SQL.query(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                .orderBy(AiMessage::getMessageTime)
                .andOrderByAsc(AiMessage::getId));
    }

    /** 只查询已落库的 AgentScope 消息 id，用于幂等回写判重。 */
    public List<AiMessage> queryMsgIdsByConversation(String conversationId) {
        return sqlExecutor.queryList(SQL.query(AiMessage.class)
                .withColumn(AiMessage::getMsgId)
                .eq(AiMessage::getConversationId, conversationId));
    }

    public void deleteByConversation(String conversationId) {
        DeleteBuilder db = SQL.delete(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId);
        sqlExecutor.delete(db);
    }
}
