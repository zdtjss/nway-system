package com.nway.system.openplatform.web.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放平台应用展示 VO（附带租户信息，不含密钥）
 */
@Data
public class OpenAppVo {

    private String id;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 租户名称（展示用）
     */
    private String tenantName;

    private String appCode;

    private String appName;

    private String status;

    private String grantedScopes;

    private String dataScopeType;

    private String dataScopeDeptIds;

    private Integer rateLimit;

    private Integer dailyLimit;

    private String allowedIps;

    private String ownerEmployeeId;

    private LocalDateTime expireTime;
}
