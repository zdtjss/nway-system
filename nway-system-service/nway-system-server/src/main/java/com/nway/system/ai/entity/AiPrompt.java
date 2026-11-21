package com.nway.system.ai.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 提示词：SYSTEM / ROLE / USER 三级，按名称去重继承。
 *
 * @author nway
 */
@Data
@Table("ai_prompt")
@EqualsAndHashCode(callSuper = false)
public class AiPrompt extends BaseEntity {

    /** 提示词名称（按名称去重） */
    private String name;

    /** SYSTEM/TASK/FEW_SHOT */
    private String promptType;

    /** SYSTEM/ROLE/USER */
    private String scopeType;

    /** SYSTEM 固定 SYSTEM；角色 id；用户工号 */
    private String scopeId;

    /** 角色编码（scopeType=ROLE 时） */
    private String roleCode;

    /** 提示词内容，支持 {{variable}} */
    private String content;

    /** 变量定义 JSON */
    private String variablesJson;

    /** 同 scope 下的优先级 */
    private Integer priority;

    /** ACTIVE/ARCHIVED */
    private String status;
}
