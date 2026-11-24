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

    /** 查询已落库消息的 id 与正文，用于幂等回写判重及原地更新。 */
    public List<AiMessage> queryMsgBlocksByConversation(String conversationId) {
        return sqlExecutor.queryList(SQL.query(AiMessage.class)
                .withColumn(AiMessage::getMsgId)
                .withColumn(AiMessage::getBlocksJson)
                .eq(AiMessage::getConversationId, conversationId));
    }

    /** 按会话 + 消息 id 更新正文：确认恢复后工具结果才到达，需要回填已落库的 tool_use 块。 */
    public void updateBlocksJson(String conversationId, String msgId, String blocksJson) {
        sqlExecutor.update(SQL.update(AiMessage.class)
                .set(AiMessage::getBlocksJson, blocksJson)
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getMsgId, msgId));
    }

    public void deleteByConversation(String conversationId) {
        DeleteBuilder db = SQL.delete(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId);
        sqlExecutor.delete(db);
    }
}
