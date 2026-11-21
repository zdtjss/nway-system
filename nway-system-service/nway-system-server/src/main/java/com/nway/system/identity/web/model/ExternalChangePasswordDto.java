package com.nway.system.identity.web.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 外部身份认证账号自助改密请求。
 *
 * @author nway
 */
@Data
public class ExternalChangePasswordDto {

    /** 工号 */
    @NotBlank(message = "工号不能为空")
    private String username;

    /** 旧密码 */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
