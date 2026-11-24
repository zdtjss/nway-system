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

    /** 视觉分析：null 按模型名自动识别，true 支持，false 不支持 */
    private Boolean supportsVision;

    /** 最大上下文 token；为空按模型名自动识别 */
    private Integer contextWindowTokens;

    private BigDecimal topP;

    private BigDecimal frequencyPenalty;

    private BigDecimal presencePenalty;

    /** 推理深度：low/medium/high/max 等 */
    private String reasoningEffort;

    /** 是否思考；为空使用模型默认行为，false 时不发送推理深度与思考预算 */
    private Boolean thinkingEnabled;

    private Integer thinkingBudget;

    /** 额外请求参数 JSON 对象（厂商专有参数） */
    private String extraBody;

    private Boolean isDefault;

    private String status;
}
