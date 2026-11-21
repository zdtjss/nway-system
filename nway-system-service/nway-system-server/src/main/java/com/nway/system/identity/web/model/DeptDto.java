package com.nway.system.identity.web.model;

import lombok.Data;

/**
 * 部门保存 DTO
 */
@Data
public class DeptDto {

    private String id;
    /** 上级部门id，顶级为空串 */
    private String parentId;
    private String deptName;
    /** 层级编码，每级2位；为空时保存自动生成 */
    private String deptCode;
    private Integer sort;
    private String status;
    /** 租户编码（超管新增时可指定归属租户，为空则取当前用户租户；子部门强制继承父部门租户） */
    private String tenantCode;
}
