package com.nway.system.identity.domain.auth;

import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.identity.domain.enums.LoginTypeEnum;

/**
 * 登录方式扩展点。
 * <p>
 * 每种登录方式（password / bio / 微信 / 企业微信 ...）对应一个实现，
 * 由 {@link AuthorizationServiceRegistry} 按 {@link LoginDto#getLoginType()} 分发。
 * 新增登录方式只需新增实现类并声明 {@link #loginType()}，无需改动登录主流程。
 */
public interface AuthorizationService {

    /**
     * 本实现支持的登录方式（同一登录方式只允许一个实现）。
     */
    LoginTypeEnum loginType();

    /**
     * 校验凭据并返回平台登录用户。
     */
    LoginUser authenticate(LoginDto loginDTO);
}
