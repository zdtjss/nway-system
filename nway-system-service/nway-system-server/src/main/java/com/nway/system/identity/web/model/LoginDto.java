package com.nway.system.identity.web.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginDto {
    /**
     * 用户名（密码登录必填；指纹/面容登录不传，按 bio key 定位用户）
     */
    private String name;

    /**
     * 密码/指纹标识/面容标识/微信用户id
     */
    @NotBlank(message = "密码不能为空")
    private String key;

    /**
     * 客户端类型（PC/APP）
     */
    private String clientType;

    /**
     * 账户验证方式（password/bio/weixin）
     */
    @NotNull(message = "登录类型不能为空")
    private String loginType;
}
