package com.nway.system.identity.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息
 */
@Data
public class UserDto {
    private String id;

    /**
     * 工号
     */
    @NotBlank(message = "工号不能为空")
    private String employeeId;
    /**
     * 真实姓名
     */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
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
     * 身份证号
     */
    private String idNumber;
    /**
     * 用户状态
     */
    private String status;
    /**
     * 是否超级管理员
     */
    private Boolean isSuperAdmin;
    /**
     * 部门id
     */
    private String deptId;
    /**
     * 部门名称（展示用，服务层批量填充）
     */
    private String deptName;
    /**
     * 租户编码
     */
    private String tenantCode;
    /**
     * 租户名称（展示用）
     */
    private String tenantName;

    private String modifierId;

    private String modifierName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifiedTime;

}
