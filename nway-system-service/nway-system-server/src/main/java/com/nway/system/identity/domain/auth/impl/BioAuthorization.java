package com.nway.system.identity.domain.auth.impl;

import com.nway.system.identity.service.UserLoginKeyService;
import com.nway.system.identity.service.UserService;
import com.nway.system.identity.domain.auth.AuthorizationService;
import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.identity.domain.enums.ClientTypeEnum;
import com.nway.system.identity.domain.enums.LoginTypeEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.identity.entity.UserLoginKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 指纹/面容登录：始终校验平台本地的生物识别凭证，与账号密码身份源配置无关。
 */
@Component("bioAuthorization")
public class BioAuthorization implements AuthorizationService {

    @Autowired
    private UserLoginKeyService loginKeyService;
    @Autowired
    private UserService userService;

    @Override
    public LoginTypeEnum loginType() {
        return LoginTypeEnum.BIO;
    }

    /**
     * 指纹/面容登录
     *
     * @param loginDTO 登录信息
     */
    @Override
    public LoginUser authenticate(LoginDto loginDTO) {
        // 登录为匿名路径，bio 标识/用户为全局查询：显式声明系统上下文（fail-closed 下无上下文读不到，
        // 否则 sys_user_login_key 的租户隔离策略会追加恒假条件，导致面容登录报『无法获取用户信息』）。
        UserLoginKey loginKey = null;
        if (ClientTypeEnum.PC.getValue().equals(loginDTO.getClientType())) {
            loginKey = TenantUtil.callAsSystem(() -> loginKeyService.getByBioPc(loginDTO.getKey()));
        }
        else if (ClientTypeEnum.MOBILE.getValue().equals(loginDTO.getClientType())) {
            loginKey = TenantUtil.callAsSystem(() -> loginKeyService.getByBioMobile(loginDTO.getKey()));
        }
        if (loginKey == null) {
            throw new NwaySystemException("无法获取用户信息");
        }
        final String employeeId = loginKey.getEmployeeId();
        return TenantUtil.callAsSystem(() -> userService.getByEmployeeId(employeeId));
    }
}
