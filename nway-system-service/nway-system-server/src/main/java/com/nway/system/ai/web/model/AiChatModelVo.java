package com.nway.system.ai.web.model;

import lombok.Data;

/**
 * 对话页可选模型视图：仅暴露选择所需字段，不返回端点与密钥信息。
 *
 * @author nway
 */
@Data
public class AiChatModelVo {

    private String id;

    /** 配置名，用于用户选择 */
    private String name;

    /** 实际模型名 */
    private String modelName;

    private Boolean isDefault;

    /** 是否支持视觉分析（已结合模型名自动识别） */
    private Boolean supportsVision;

    /** 是否开启思考（已结合推理深度/思考预算推断） */
    private Boolean thinkingEnabled;

    /** 推理深度：low/medium/high/max 等 */
    private String reasoningEffort;

    /** 最大上下文 token */
    private Integer contextWindowTokens;
}
