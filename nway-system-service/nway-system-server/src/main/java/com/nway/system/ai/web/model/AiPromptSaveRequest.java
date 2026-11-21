package com.nway.system.ai.web.model;

import lombok.Data;

/**
 * 提示词保存请求。
 *
 * @author nway
 */
@Data
public class AiPromptSaveRequest {

    private String id;

    private String name;

    /** SYSTEM/TASK/FEW_SHOT */
    private String promptType;

    /** SYSTEM/ROLE/USER */
    private String scopeType;

    private String scopeId;

    private String roleCode;

    private String content;

    private String variablesJson;

    private Integer priority;

    private String status;
}
