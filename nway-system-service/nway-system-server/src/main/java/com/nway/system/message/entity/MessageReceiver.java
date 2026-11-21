package com.nway.system.message.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 消息接收人明细，对应数据库表 message_receiver
 */
@Data
@Table("message_receiver")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class MessageReceiver extends BaseEntity {

    private String messageId;
    private String userId;
    /** 是否已读：0-未读，1-已读 */
    private Boolean isRead;
    /** 是否为新消息（用于轮询）：0-否，1-是 */
    private Boolean isNew;
    private LocalDateTime readTime;
    /** 计划发送的渠道列表，JSON 数组，如 ["inner","email"] */
    private String channels;
    /** 各渠道发送状态，JSON 对象，如 {"inner":1,"email":0} */
    private String channelStatus;
}
