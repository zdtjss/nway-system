package com.nway.system.identity.domain.auth.external;

import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 外部身份系统的账号密码校验器。
 * <p>
 * 只负责凭据真实性校验与外部用户档案获取，平台账号落地交给 {@link ExternalUserProvisioner}。
 *
 * @author nway
 */
@Component
public class ExternalPasswordAuthenticator {

    @Autowired
    private ExternalAuthProperties properties;
    @Autowired
    private ExternalAuthClient externalAuthClient;
    @Autowired
    private ExternalUserProvisioner externalUserProvisioner;

    public LoginUser authenticate(LoginDto loginDTO) {
        if (!properties.isEnabled()) {
            throw new NwaySystemException(ExternalAuthClient.NOT_CONFIGURED, "外部身份认证登录未启用");
        }
        ExternalAuthClient.ExternalUser externalUser =
                externalAuthClient.authenticate(loginDTO.getName(), loginDTO.getKey());
        return externalUserProvisioner.resolve(externalUser, loginDTO.getName());
    }

    /**
     * 外部账号自助改密（登录前密码已过期场景），由外部系统校验旧密码。
     */
    public void changePassword(String account, String oldPassword, String newPassword) {
        externalAuthClient.changePassword(account, oldPassword, newPassword);
    }
}
