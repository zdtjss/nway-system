package com.nway.system.identity.web.model;

import lombok.Data;

/**
 * 岗位下任职用户 VO（含用户姓名与岗位所在部门名称）
 */
@Data
public class PositionUserVo {

    /** 关联记录id */
    private String id;
    /** 工号 */
    private String employeeId;
    /** 用户姓名 */
    private String realName;
    private String positionId;
    /** 岗位所在部门id（空=全局岗位） */
    private String deptId;
    /** 岗位所在部门名称 */
    private String deptName;
    /** 是否主岗 */
    private Boolean isPrimary;
}
