package com.nway.system.message.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.spring.jdbc.sql.builder.UpdateBuilder;
import com.nway.spring.jdbc.sql.permission.WhereCondition;
import com.nway.system.message.entity.MessageReceiver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Repository
public class MessageReceiverDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insertBatch(List<MessageReceiver> receiverList) {
        sqlExecutor.batchInsert(receiverList);
    }

    public Page<MessageReceiver> getNewUnreadMessages(String userId) {
        QueryBuilder queryBuilder = SQL.query(MessageReceiver.class)
                .eq(MessageReceiver::getUserId, userId)
                .eq(MessageReceiver::getIsRead, false)
                .eq(MessageReceiver::getIsNew, true)
                .appendCondition(new WhereCondition("JSON_CONTAINS(channels, ?)", "\"inner\""))
                .orderByDesc(MessageReceiver::getCreateTime);
        return sqlExecutor.queryPage(queryBuilder, 1, 5);
    }

    public List<MessageReceiver> queryUserMessage(String userId, List<String> messageIdList) {
        if (CollectionUtils.isEmpty(messageIdList)) {
            return List.of();
        }
        QueryBuilder queryBuilder = SQL.query(MessageReceiver.class)
                .eq(MessageReceiver::getUserId, userId)
                .in(MessageReceiver::getMessageId, messageIdList)
                .orderByDesc(MessageReceiver::getCreateTime);
        return sqlExecutor.queryList(queryBuilder);
    }

    /**
     * 按接收人 + 接收时间范围查全部接收记录（个人消息列表的主查询）。
     * <p>
     * 以接收记录为基准而非消息主体：跨租户发送时消息主体归属发送人租户，
     * 接收人在自己租户下先查主体会恒为空；接收记录已按接收人租户归属。
     */
    public List<MessageReceiver> queryByUserAndTime(String userId, java.time.LocalDateTime begin,
                                                    java.time.LocalDateTime end) {
        QueryBuilder queryBuilder = SQL.query(MessageReceiver.class).ignoreInvalidDeep(true)
                .eq(MessageReceiver::getUserId, userId)
                .between(MessageReceiver::getCreateTime, begin, end)
                .orderByDesc(MessageReceiver::getCreateTime);
        return sqlExecutor.queryList(queryBuilder);
    }

    public void markAsRead(String receiveId) {
        sqlExecutor.update(SQL.update(MessageReceiver.class)
                .set(MessageReceiver::getIsRead, true)
                .set(MessageReceiver::getIsNew, false)
                .eq(MessageReceiver::getId, receiveId));
    }

    public void markAsNotNew(List<String> receiverIds) {
        if (CollectionUtils.isEmpty(receiverIds)) {
            return;
        }
        sqlExecutor.update(SQL.update(MessageReceiver.class)
                .set(MessageReceiver::getIsNew, false)
                .in(MessageReceiver::getId, receiverIds));
    }

    public List<MessageReceiver> selectList(String messageId, List<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return List.of();
        }
        QueryBuilder queryBuilder = SQL.query(MessageReceiver.class)
                .eq(MessageReceiver::getMessageId, messageId)
                .in(MessageReceiver::getUserId, userIds);
        return sqlExecutor.queryList(queryBuilder);
    }

    public int update(MessageReceiver updateEntity) {
        UpdateBuilder updateBuilder = SQL.update(MessageReceiver.class)
                .set(MessageReceiver::getIsRead, updateEntity.getIsRead())
                .set(MessageReceiver::getIsNew, updateEntity.getIsNew())
                .set(MessageReceiver::getReadTime, updateEntity.getReadTime())
                .eq(MessageReceiver::getId, updateEntity.getId());
        return sqlExecutor.update(updateBuilder);
    }

    public void updateBatchById(List<MessageReceiver> list) {
        sqlExecutor.batchUpdateById(list);
    }

    public MessageReceiver selectOne(QueryBuilder queryBuilder) {
        return sqlExecutor.queryFirst(queryBuilder);
    }

    public List<MessageReceiver> queryByMessageIds(List<String> messageIds) {
        if (CollectionUtils.isEmpty(messageIds)) {
            return List.of();
        }
        QueryBuilder queryBuilder = SQL.query(MessageReceiver.class)
                .in(MessageReceiver::getMessageId, messageIds);
        return sqlExecutor.queryList(queryBuilder);
    }

    public MessageReceiver selectById(String id) {
        return sqlExecutor.queryById(id, MessageReceiver.class);
    }

}
