package com.nway.system.openplatform.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放平台调用日志展示 VO（附带租户信息）
 */
@Data
public class OpenApiLogVo {

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

    private String apiPath;

    private String httpMethod;

    private String requestParams;

    private Integer responseStatus;

    private Integer durationMs;

    private String ip;

    private String employeeId;

    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
