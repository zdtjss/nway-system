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

    private Boolean isDefault;

    private String status;

    private boolean apiKeyConfigured;

    private LocalDateTime modifiedTime;
}
