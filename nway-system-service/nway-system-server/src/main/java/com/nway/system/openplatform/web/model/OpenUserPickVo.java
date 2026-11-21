package com.nway.system.openplatform.web.model;

import lombok.Data;

/**
 * 选人组件对外返回的用户简要信息（开放平台 /open/user/search）。
 * <p>
 * 仅包含选择器展示所需字段，不含身份证号、Token、租户等内部或敏感信息。
 *
 * @author nway
 */
@Data
public class OpenUserPickVo {

    /** 工号 */
    private String employeeId;

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phoneNumber;

    /** 邮箱 */
    private String email;

    /** 头像 */
    private String avatar;

    /** 用户状态（using/disabled） */
    private String status;

    /** 主部门 id */
    private String deptId;

    /** 主部门名称 */
    private String deptName;

}