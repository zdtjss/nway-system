package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 登录态 → AI 用户上下文。
 *
 * @author nway
 */
public final class AiUserContexts {

    private AiUserContexts() {
    }

    public static AiUserContext current() {
        LoginUser loginUser = LoginUserContext.current();
        if (loginUser == null) {
            throw new NwaySystemException(ErrorCodeEnum.UNAUTHORIZED);
        }
        AiUserContext user = new AiUserContext();
        user.setEmployeeId(loginUser.getEmployeeId());
        user.setEmployeeName(loginUser.getRealName());
        user.setTenantCode(loginUser.getTenantCode());
        user.setWorkTenantCode(loginUser.getCurrentWorkTenantCode());
        user.setRoleCodes(CollectionUtils.isEmpty(loginUser.getRoleList())
                ? List.of() : loginUser.getRoleList());
        user.setDeptCodes(CollectionUtils.isEmpty(loginUser.getDeptCodeList())
                ? List.of() : loginUser.getDeptCodeList());
        user.setToken(loginUser.getToken());
        user.setSuperAdmin(loginUser.isSuperAdmin());
        user.setAdmin(loginUser.isAdmin());
        return user;
    }
}
