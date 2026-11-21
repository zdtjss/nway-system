package com.nway.system.identity.domain.auth.impl;

import com.nway.system.identity.domain.auth.AuthorizationService;
import com.nway.system.identity.domain.auth.external.ExternalAuthClient;
import com.nway.system.identity.domain.auth.external.ExternalAuthProperties;
import com.nway.system.identity.domain.auth.external.ExternalPasswordAuthenticator;
import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.identity.domain.enums.LoginTypeEnum;
import com.nway.system.common.exception.NwaySystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 账号密码登录（loginType=password）。
 * <p>
 * 凭据校验身份源对前端透明，由服务端配置决定：
 * <ul>
 *   <li>外部身份认证开启（nway.login.external.enabled=true）：优先外部身份系统校验；</li>
 *   <li>外部系统判定账号不存在且允许回退：转平台本地 BCrypt 校验，保留平台管理员登录入口；</li>
 *   <li>外部身份认证关闭：直接平台本地校验。</li>
 * </ul>
 * 密码错误、外部服务不可用等情况不降级，避免绕过外部身份源的安全策略。
 *
 * @author nway
 */
@Slf4j
@Component("passwordAuthorization")
public class PasswordAuthorization implements AuthorizationService {

    @Autowired
    private ExternalAuthProperties externalAuthProperties;
    @Autowired
    private ExternalPasswordAuthenticator externalPasswordAuthenticator;
    @Autowired
    private LocalPasswordAuthenticator localPasswordAuthenticator;

    @Override
    public LoginTypeEnum loginType() {
        return LoginTypeEnum.PASSWORD;
    }

    @Override
    public LoginUser authenticate(LoginDto loginDTO) {
        if (!externalAuthProperties.isEnabled()) {
            return localPasswordAuthenticator.authenticate(loginDTO);
        }
        try {
            return externalPasswordAuthenticator.authenticate(loginDTO);
        } catch (NwaySystemException e) {
            if (externalAuthProperties.isLocalFallback()
                    && e.getCode() == ExternalAuthClient.ACCOUNT_NOT_FOUND) {
                log.info("外部认证中不存在该账号，回退平台本地认证 account={}", loginDTO.getName());
                return localPasswordAuthenticator.authenticate(loginDTO);
            }
            throw e;
        }
    }
}
