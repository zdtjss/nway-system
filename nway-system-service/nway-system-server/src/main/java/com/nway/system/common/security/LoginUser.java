package com.nway.system.common.security;

import lombok.Data;

import java.util.List;

@Data
public class LoginUser {
    /**
     * 工号
     */
    private String employeeId;
    /**
     * token
     */
    private String token;
    /**
     * 真实姓名
     */
    private String realName;
    /**
     * 头像
     */
    private String avatar;
    /**
     * 所属部门编码，第一个为主部门
     */
    private List<String> deptCodeList;
    /**
     * 角色编码列表
     */
    private List<String> roleList;
    /**
     * 租户编码
     */
    private String tenantCode;
    /**
     * 租户名称
     */
    private String tenantName;
    /**
     * 是否超级管理员（超管可跨租户）
     */
    private boolean superAdmin;
    /**
     * 是否平台管理员（超管任命，非超管）
     */
    private boolean admin;
    /**
     * 管理员可管理的租户编码列表（授权租户）
     */
    private List<String> adminTenantCodes;
    /**
     * 主部门id（sys_user.dept_id）
     */
    private String mainDeptId;
    /**
     * 主部门编码（用于数据权限前缀匹配）
     */
    private String mainDeptCode;
    /**
     * 用户状态：using / disabled
     */
    private String status;
    /**
     * 岗位编码列表
     */
    private List<String> positionCodes;
    /**
     * Token 版本号（从 JWT claims 中解析）
     */
    private int tokenVersion;
    /**
     * 会话标识（从 JWT claims 中解析，用于多设备会话管理）
     */
    private String sessionId;
    /**
     * 是否需先修改初始密码（新建用户/管理员重置密码后为 true）
     */
    private boolean pwdResetRequired;
    /**
     * Token 续期阈值（毫秒）：剩余不足该时间时前端调用 /login/refresh 续期。
     * 由后端登录时下发（nway.jwt.refresh-threshold-millis），保证前后端阈值一致。
     */
    private long refreshThresholdMillis;
    /**
     * 管理员当前工作租户编码（通过 X-Work-Tenant Header 传入，限定管理操作范围）
     */
    private String currentWorkTenantCode;
}
