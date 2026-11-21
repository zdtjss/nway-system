package com.nway.system.ai.web.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词视图。
 *
 * @author nway
 */
@Data
public class AiPromptVo {

    private String id;

    private String name;

    private String promptType;

    private String scopeType;

    private String scopeId;

    private String roleCode;

    private String content;

    private String variablesJson;

    private Integer priority;

    private String status;

    /** 来源展示：系统/角色/个人 */
    private String scopeLabel;

    private LocalDateTime modifiedTime;
}
