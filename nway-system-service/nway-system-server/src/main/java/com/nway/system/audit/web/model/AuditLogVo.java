package com.nway.system.audit.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志展示 VO（附带租户信息）
 */
@Data
public class AuditLogVo {

    private String id;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 租户名称（展示用）
     */
    private String tenantName;

    private String operatorId;

    private String operatorName;

    private String operationType;

    private String apiPath;

    private String httpMethod;

    private String requestParams;

    private Integer responseStatus;

    private Integer durationMs;

    private String ip;

    private String userAgent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
