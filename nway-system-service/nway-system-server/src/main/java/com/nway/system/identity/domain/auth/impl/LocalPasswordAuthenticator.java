package com.nway.system.identity.domain.auth.impl;

import com.nway.system.identity.service.UserLoginKeyService;
import com.nway.system.identity.service.UserService;
import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.identity.entity.UserLoginKey;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 平台本地密码校验（BCrypt）。
 * <p>
 * 仅当外部身份认证未启用、或外部系统判定账号不存在且允许回退时使用。
 *
 * @author nway
 */
@Component
public class LocalPasswordAuthenticator {

    @Autowired
    private UserService userService;

    @Autowired
    private UserLoginKeyService loginKeyService;

    public LoginUser authenticate(LoginDto loginDTO) {
        // 密码登录必须提供用户名（DTO 层不校验 name，bio 登录不需要）
        if (StringUtils.isBlank(loginDTO.getName())) {
            throw new NwaySystemException("用户名不能为空");
        }
        // 登录为匿名路径，登录名/用户为全局查询：显式声明系统上下文（fail-closed 下无上下文读不到）
        UserLoginKey loginKey = TenantUtil.callAsSystem(() -> loginKeyService.getByLoginName(loginDTO.getName()));
        if (loginKey == null) {
            throw new NwaySystemException("用户名或密码错误");
        }
        // BCrypt 验证
        if (!loginKeyService.matchPassword(loginDTO.getKey(), loginKey.getPassword())) {
            throw new NwaySystemException("用户名或密码错误");
        }
        return TenantUtil.callAsSystem(() -> userService.getByEmployeeId(loginKey.getEmployeeId()));
    }
}
