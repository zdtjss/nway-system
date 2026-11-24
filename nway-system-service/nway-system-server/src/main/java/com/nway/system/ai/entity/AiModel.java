package com.nway.system.ai.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 模型 Provider（OpenAI 兼容端点）。
 *
 * @author nway
 */
@Data
@Table("ai_model")
@EqualsAndHashCode(callSuper = false)
public class AiModel extends BaseEntity {

    /** 配置名 */
    private String name;

    /** OpenAI 兼容端点 */
    private String baseUrl;

    /** AES-GCM 密文 */
    private String apiKeyCipher;

    /** 模型名 */
    private String modelName;

    private BigDecimal temperature;

    /** 单次输出上限 */
    private Integer maxTokens;

    /** 是否支持工具调用 */
    private Boolean supportsToolCalling;

    /** 是否支持视觉分析；为空时按模型名自动识别 */
    private Boolean supportsVision;

    /** 最大上下文 token；为空时按模型名自动识别 */
    private Integer contextWindowTokens;

    /** 核采样 top_p */
    private BigDecimal topP;

    /** 频率惩罚 -2 ~ 2 */
    private BigDecimal frequencyPenalty;

    /** 存在惩罚 -2 ~ 2 */
    private BigDecimal presencePenalty;

    /** 推理深度：low/medium/high/max 等 */
    private String reasoningEffort;

    /** 是否思考；为空表示使用模型默认行为 */
    private Boolean thinkingEnabled;

    /** 思考 token 预算 */
    private Integer thinkingBudget;

    /** 额外请求参数 JSON（厂商专有参数，如 enable_thinking） */
    private String extraBody;

    /** 是否默认 Provider */
    private Boolean isDefault;

    /** ACTIVE/DISABLED */
    private String status;
}
