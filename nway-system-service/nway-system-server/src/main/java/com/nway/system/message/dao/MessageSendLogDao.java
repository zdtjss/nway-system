package com.nway.system.message.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.UpdateBuilder;
import com.nway.system.message.domain.enums.MessageSendStatusEnum;
import com.nway.system.message.entity.MessageSendLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MessageSendLogDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(MessageSendLog log) {
        sqlExecutor.insert(log);
    }

    public void insertBatch(List<MessageSendLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        sqlExecutor.batchInsert(logs);
    }

    public List<MessageSendLog> queryByMessage(String messageId) {
        return sqlExecutor.queryList(SQL.query(MessageSendLog.class)
                .eq(MessageSendLog::getMessageId, messageId));
    }

    /**
     * 查询指定消息、接收人、渠道的发送记录（同一接收人同一渠道仅一条）
     */
    public MessageSendLog queryByReceiverChannel(String messageId, String receiverId, String channel) {
        return sqlExecutor.queryFirst(SQL.query(MessageSendLog.class)
                .eq(MessageSendLog::getMessageId, messageId)
                .eq(MessageSendLog::getReceiverId, receiverId)
                .eq(MessageSendLog::getChannel, channel));
    }

    /**
     * 查询需要重试的失败记录：状态为 failed 且到达重试时间且未超过最大重试次数。
     * <p>
     * 该查询为<b>显式声明的系统级跨租户查询</b>（仅由定时重试任务调用，见 MessageRetryScheduler），
     * 因此不使用 ORM 自动隔离，而是直接构造带 is_deleted 过滤的参数化 SQL；
     * 同时避免使用 SqlBuilder.getSql()（该方法有状态，重复调用会重复追加权限条件）。
     * 调用方必须处于 {@code TenantUtil.callAsSystem} 声明的系统上下文中。
     *
     * @param now 当前时间（next_retry_time 早于该时刻才重试）
     * @param limit 单次扫描上限
     */
    public List<MessageSendLog> queryRetryCandidates(LocalDateTime now, int limit) {
        String sql = "SELECT * FROM message_send_log WHERE is_deleted = 0 AND status = ? "
                + "AND (next_retry_time IS NULL OR next_retry_time <= ?) "
                + "AND retry_count < max_retry "
                + "ORDER BY next_retry_time ASC LIMIT ?";
        return sqlExecutor.queryList(sql, MessageSendLog.class,
                MessageSendStatusEnum.FAILED.getCode(), now, limit);
    }

    public int updateStatus(String id, String status, String errorMsg, LocalDateTime sendTime) {
        UpdateBuilder ub = SQL.update(MessageSendLog.class)
                .set(MessageSendLog::getStatus, status)
                .set(MessageSendLog::getErrorMsg, errorMsg)
                .set(MessageSendLog::getSendTime, sendTime)
                .eq(MessageSendLog::getId, id);
        return sqlExecutor.update(ub);
    }

    public void updateBatchById(List<MessageSendLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        sqlExecutor.batchUpdateById(logs);
    }
}
