package com.nway.system.ai.web.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型配置视图：密钥只返回是否已配置，不返回密文/明文。
 *
 * @author nway
 */
@Data
public class AiModelVo {

    private String id;

    private String name;

    private String baseUrl;

    private String modelName;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Boolean supportsToolCalling;

    /** 视觉分析配置：null 自动识别，true 支持，false 不支持 */
    private Boolean supportsVision;

    /** 视觉分析最终生效结果（配置为空时按模型名自动识别） */
    private Boolean visionSupported;

    private Integer contextWindowTokens;

    private BigDecimal topP;

    private BigDecimal frequencyPenalty;

    private BigDecimal presencePenalty;

    private String reasoningEffort;

    private Boolean thinkingEnabled;

    private Integer thinkingBudget;

    private String extraBody;

    private Boolean isDefault;

    private String status;

    private boolean apiKeyConfigured;

    private LocalDateTime modifiedTime;
}
