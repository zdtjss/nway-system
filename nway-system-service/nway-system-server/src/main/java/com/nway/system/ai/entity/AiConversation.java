package com.nway.system.ai.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 会话索引：只存标题、用户、时间，消息正文由 AgentScope Session 承载。
 *
 * @author nway
 */
@Data
@Table("ai_conversation")
@EqualsAndHashCode(callSuper = false)
public class AiConversation extends BaseEntity {

    /** 用户工号 */
    private String userId;

    /** Agent 标识 */
    private String agentId;

    /** 标题（首条消息或模型生成） */
    private String title;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

    /** ACTIVE/ARCHIVED */
    private String status;
}
