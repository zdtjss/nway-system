package com.nway.system.ai.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 会话消息：消息正文落库，历史记录不再依赖 AgentScope 本地 journal。
 *
 * @author nway
 */
@Data
@Table("ai_message")
@EqualsAndHashCode(callSuper = false)
public class AiMessage extends BaseEntity {

    /** AgentScope 消息 id，仅用于会话内幂等判重（模型响应 id 不保证全局唯一） */
    private String msgId;

    /** 会话 id */
    private String conversationId;

    /** 用户工号 */
    private String userId;

    /** user/assistant */
    private String role;

    /** 消息块 JSON 数组（text/thinking/tool_use），结构与 AiMessageVo.blocks 一致 */
    private String blocksJson;

    /** 消息时间（沿用 SDK 时间戳格式） */
    private String messageTime;
}
