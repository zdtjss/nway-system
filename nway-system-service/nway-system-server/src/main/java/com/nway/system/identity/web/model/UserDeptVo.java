package com.nway.system.identity.web.model;

import lombok.Data;

import java.util.List;

/**
 * 部门下任职用户 VO（含用户信息与任职部门列表，支持跨部门展示）
 */
@Data
public class UserDeptVo {

    /** 用户id */
    private String id;
    /** 工号 */
    private String employeeId;
    /** 姓名 */
    private String realName;
    private String phoneNumber;
    private String email;
    private String status;
    /** 任职部门列表（主部门排首位） */
    private List<DeptRef> depts;

    @Data
    public static class DeptRef {
        private String deptId;
        private String deptName;
        /** 是否主部门 */
        private Boolean primary;
    }
}
