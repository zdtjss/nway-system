package com.nway.system.identity.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门树形 VO
 */
@Data
public class DeptTreeVO {

    private String id;
    /** 层级编码，每级2位 */
    private String deptCode;
    private String deptName;
    private String parentId;
    /** 层级（1=根，2=二级...），由 deptCode 长度推导 */
    private Integer level;
    private Integer sort;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 租户编码 */
    private String tenantCode;
    /** 租户名称 */
    private String tenantName;
    /** 子部门 */
    private List<DeptTreeVO> children;
}
