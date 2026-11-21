package com.nway.system.message.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 消息发送日志，按渠道、按接收人记录每次发送结果，支持失败重试与可观测
 */
@Data
@Table("message_send_log")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class MessageSendLog extends BaseEntity {

    private String messageId;
    private String receiverId;
    private String userId;
    /** 发送渠道：inner/inner_pop/email/wxwork */
    private String channel;
    /** 发送状态：pending/success/failed */
    private String status;
    private String errorMsg;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime sendTime;
    private LocalDateTime nextRetryTime;
}
