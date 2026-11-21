package com.nway.system.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台内部服务接口配置（nway.internal.service）。
 * <p>
 * 供 workflow-engine 等平台内服务调用 /internal/** 使用：独立密钥、短有效期服务令牌，
 * 与用户 Token、开放平台 Token 相互隔离。secret 为空时内部服务接口一律拒绝（fail-closed）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "nway.internal.service")
public class InternalServiceProperties {

    /** 内部服务令牌签名密钥（须与调用方配置一致，长度不小于 32 字节）。 */
    private String secret = "";

    /** 允许的签发方（iss）。 */
    private List<String> allowedIssuers = List.of("workflow");

    /** 期望受众（aud）。 */
    private String audience = "nway-system";

    /** 内部接口要求的 scope。 */
    private String requiredScope = "msg:send";
}
