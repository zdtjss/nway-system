package com.nway.system.identity.domain.enums;

import com.nway.system.common.exception.NwaySystemException;

import java.util.Arrays;

/**
 * 登录方式（凭据类型）枚举。
 * <p>
 * 只描述「使用何种凭据登录」，不描述凭据由谁校验：账号密码的身份源
 * （平台本地 / 外部统一身份认证）由服务端配置决定，前端无需感知。
 * 新增登录方式（如微信 weixin、企业微信 wecom）时在此登记，并提供对应的
 * {@code com.nway.system.identity.domain.auth.AuthorizationService} 实现。
 */
public enum LoginTypeEnum {
    /**
     * 账号密码登录（校验身份源由服务端配置路由：本地 BCrypt / 外部身份认证）
     */
    PASSWORD("password"),
    /**
     * 指纹/面容识别登录（平台本地生物识别凭证）
     */
    BIO("bio"),
    ;

    /**
     * 历史登录类型：外部身份认证曾被建模为独立登录方式，
     * 现改为服务端按配置自动路由，保留该值兼容旧调用方（等同 password）。
     */
    private static final String LEGACY_EXTERNAL = "external";

    private final String code;

    LoginTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }

    public static LoginTypeEnum of(String name) {
        if (LEGACY_EXTERNAL.equals(name)) {
            return PASSWORD;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode().equals(name))
                .findFirst()
                .orElseThrow(() -> new NwaySystemException("无效的登录类型"));
    }
}
