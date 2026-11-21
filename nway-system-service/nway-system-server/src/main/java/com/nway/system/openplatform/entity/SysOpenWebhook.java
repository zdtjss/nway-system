package com.nway.system.openplatform.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开放平台 Webhook 订阅。
 *
 * @author nway
 */
@Data
@Table("sys_open_webhook")
@EqualsAndHashCode(callSuper = false)
public class SysOpenWebhook extends BaseEntity {

    /**
     * 应用 id
     */
    private String appId;
    /**
     * 事件类型：user.created/user.updated/user.disabled/dept.changed/role.changed/data_permission.changed
     */
    private String eventType;
    /**
     * 回调 URL
     */
    private String callbackUrl;
    /**
     * HMAC 签名密钥
     */
    private String secret;
    /**
     * 状态：using/disabled
     */
    private String status;
    /**
     * 重试次数
     */
    private Integer retryCount;
}
