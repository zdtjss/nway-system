package com.nway.system.identity.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 用户信息
 */
@Data
@Table("sys_user")
public class User extends BaseEntity {
    /**
     * 工号
     */
    private String employeeId;
    /**
     * 真实姓名
     */
    private String realName;
    /**
     * 头像
     */
    private String avatar;
    /**
     * 手机号
     */
    private String phoneNumber;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 部门id
     */
    private String deptId;
    /**
     * 身份证号
     */
    private String idNumber;
    /**
     * 用户状态
     */
    private String status;
    /**
     * 是否超级管理员（超管可跨租户）
     */
    private Boolean isSuperAdmin;
    /**
     * 是否平台管理员（超管任命，可管理指定业务/租户范围内资源）
     */
    private Boolean isAdmin;
    /**
     * Token 版本号，强制下线时自增
     */
    private Integer tokenVersion;

}
