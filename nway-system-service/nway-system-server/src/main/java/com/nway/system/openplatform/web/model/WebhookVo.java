package com.nway.system.openplatform.web.model;

import lombok.Data;

/**
 * Webhook 订阅展示 VO（附带租户信息）
 */
@Data
public class WebhookVo {

    private String id;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 租户名称（展示用）
     */
    private String tenantName;

    private String appId;

    private String eventType;

    private String callbackUrl;

    private String secret;

    private String status;

    private Integer retryCount;
}
