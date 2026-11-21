package com.nway.system.ai.web.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 模型配置保存请求。
 *
 * @author nway
 */
@Data
public class AiModelSaveRequest {

    private String id;

    private String name;

    private String baseUrl;

    /** 明文 API Key；编辑时留空表示不修改 */
    private String apiKey;

    private String modelName;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Boolean supportsToolCalling;

    private Boolean isDefault;

    private String status;
}
