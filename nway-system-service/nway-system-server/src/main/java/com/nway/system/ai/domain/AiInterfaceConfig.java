package com.nway.system.ai.domain;

import lombok.Data;

import java.util.List;

/**
 * 接口配置（对应 ai-interface.schema.json interface）。
 *
 * @author nway
 */
@Data
public class AiInterfaceConfig {

    /** 管理端编辑时回传，用于按主键定位记录；新建/导入时为空 */
    private String id;

    private String interfaceCode;

    private String name;

    private String description;

    private String appCode;

    private String httpMethod;

    private String path;

    private String contentType;

    /** READ/WRITE/HIGH_RISK */
    private String riskLevel;

    private Boolean confirmRequired;

    private List<AiFieldDef> inputSchema;

    private List<AiOutputField> outputSchema;

    private AiInvokeConfig invokeConfig;

    private String searchKeywords;

    private List<String> examples;

    /** AUTO/MERGED/HUMAN_REVIEWED */
    private String metadataConfidence;

    /** DRAFT/PUBLISHED/OFFLINE */
    private String status;

    /** PUBLIC：所有具备 AI 对话权限的用户可调用；RESTRICTED：需显式授权 execute */
    private String accessMode;

    private Integer version;

    private String tenantCode;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    public boolean isWrite() {
        return "WRITE".equalsIgnoreCase(riskLevel) || "HIGH_RISK".equalsIgnoreCase(riskLevel);
    }
}
