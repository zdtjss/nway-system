package com.nway.system.metadata.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置展示 VO（附带租户信息）
 */
@Data
public class SysConfigVo {

    private String id;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 租户名称（展示用）
     */
    private String tenantName;

    private String configKey;

    private String configValue;

    private String configName;

    private String configGroup;

    private String remark;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifiedTime;
}
