package com.nway.system.identity.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位展示 VO（附带租户信息）
 */
@Data
public class PositionVo {

    private String id;

    private String positionCode;

    private String positionName;

    /**
     * 所属部门id（岗位唯一归属组织）
     */
    private String deptId;

    /**
     * 所属部门名称（展示用）
     */
    private String deptName;

    private String positionLevel;

    private String status;

    private String remark;

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
}
