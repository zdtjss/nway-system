package com.nway.system.portal.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用展示 VO（附带租户信息）
 */
@Data
public class AppVo {

    private String id;

    /** 应用标识（全局唯一） */
    private String appCode;

    /** 应用名称 */
    private String appName;

    /** 前端部署地址（微前端加载入口） */
    private String host;

    /** 后端 API 基础地址 */
    private String apiBaseUrl;

    /** 状态：using/disabled */
    private String status;

    /** 排序 */
    private Integer sort;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 租户名称（展示用）
     */
    private String tenantName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifiedTime;
}
