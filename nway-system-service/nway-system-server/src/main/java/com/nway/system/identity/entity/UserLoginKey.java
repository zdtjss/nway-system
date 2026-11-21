package com.nway.system.identity.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 登录校验信息
 */
@Data
@Table("sys_user_login_key")
public class UserLoginKey extends BaseEntity {
    /**
     * 用户工号
     */
    private String employeeId;
    /**
     * 登录名
     */
    private String loginName;
    /**
     * 密码
     */
    private String password;
    /**
     * 移动端指纹/面容标识
     */
    private String bioMobile;
    /**
     * PC端指纹/面容标识
     */
    private String bioPc;
    /**
     * 微信id
     */
    private String weixinId;
    /**
     * 是否有效
     */
    private Boolean isValid;
    /**
     * 是否要求修改密码（新建用户/管理员重置密码后置 true，用户自行改密后清 false）
     */
    private Boolean pwdResetRequired;
}
