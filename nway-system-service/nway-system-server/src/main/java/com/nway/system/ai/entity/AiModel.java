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

    private Integer maxTokens;

    /** 是否支持工具调用 */
    private Boolean supportsToolCalling;

    /** 是否默认 Provider */
    private Boolean isDefault;

    /** ACTIVE/DISABLED */
    private String status;
}
