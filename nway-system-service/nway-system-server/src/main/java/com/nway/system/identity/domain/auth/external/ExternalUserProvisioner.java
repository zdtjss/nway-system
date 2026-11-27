package com.nway.system.identity.domain.auth.external;

import com.nway.system.identity.service.LoginUserAssembler;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.identity.domain.enums.UserStatusEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import com.nway.system.identity.entity.UserRole;
import com.nway.system.identity.dao.UserRoleDao;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 外部身份 → 平台登录用户映射器。
 * <p>
 * 身份真实性由外部系统负责，本类只做本地落地：租户校验、影子账号补齐、登录态装配。
 * 与具体认证协议无关，微信/企业微信等后续外部身份接入可直接复用。
 *
 * @author nway
 */
@Slf4j
@Component
public class ExternalUserProvisioner {

    /** 本地租户未初始化 */
    private static final int TENANT_NOT_INITIALIZED = 1021;

    @Autowired
    private ExternalAuthProperties properties;
    @Autowired
    private UserDao userDao;
    @Autowired
    private UserRoleDao userRoleDao;
    @Autowired
    private TenantDao tenantDao;
    @Autowired
    private LoginUserAssembler loginUserAssembler;

    /**
     * 把外部用户映射为平台登录用户：本地已存在直接装配，不存在则按配置补建影子账号。
     * <p>
     * 登录为匿名路径且登录名/用户为全局查询，显式声明系统上下文。
     */
    public LoginUser resolve(ExternalAuthClient.ExternalUser externalUser, String loginName) {
        return TenantUtil.callAsSystem(() -> doResolve(externalUser, loginName));
    }

    private LoginUser doResolve(ExternalAuthClient.ExternalUser externalUser, String loginName) {
        String employeeId = StringUtils.lowerCase(StringUtils.trimToNull(
                StringUtils.defaultIfBlank(externalUser.getEmployeeId(), loginName)));
        if (StringUtils.isBlank(employeeId)) {
            throw new NwaySystemException(ExternalAuthClient.USER_NOT_FOUND, "外部认证未返回工号");
        }
        String tenantCode = properties.getTenantCode();
        if (tenantDao.getByCode(tenantCode) == null) {
            throw new NwaySystemException(TENANT_NOT_INITIALIZED, "外部用户租户未初始化：" + tenantCode + "，请先执行租户初始化脚本");
        }
        User user = userDao.getByEmployeeId(employeeId);
        if (user == null) {
            if (!properties.isAutoProvision()) {
                throw new NwaySystemException(ExternalAuthClient.USER_NOT_FOUND, "账号尚未开通，请联系管理员");
            }
            createShadowUser(externalUser, employeeId, tenantCode);
        } else if (UserStatusEnum.DISABLED.getValue().equals(user.getStatus())) {
            throw new NwaySystemException(ErrorCodeEnum.ACCOUNT_LOCKED);
        }
        LoginUser loginUser = loginUserAssembler.getByEmployeeId(employeeId);
        if (loginUser == null) {
            throw new NwaySystemException(ExternalAuthClient.USER_NOT_FOUND, "账号初始化失败，请联系管理员");
        }
        return loginUser;
    }

    /**
     * 补建影子账号（仅承载登录态/菜单授权/审计，组织与字典主数据仍在外部系统）。
     */
    private void createShadowUser(ExternalAuthClient.ExternalUser externalUser, String employeeId, String tenantCode) {
        User user = new User();
        user.setEmployeeId(employeeId);
        user.setRealName(StringUtils.defaultIfBlank(externalUser.getRealName(), employeeId));
        user.setEmail(externalUser.getEmail());
        user.setPhoneNumber(externalUser.getMobile());
        user.setAvatar(externalUser.getAvatar());
        user.setStatus(UserStatusEnum.USING.getValue());
        user.setIsSuperAdmin(Boolean.FALSE);
        user.setIsAdmin(Boolean.FALSE);
        user.setTokenVersion(0);
        user.setTenantCode(tenantCode);

        UserRole userRole = new UserRole();
        userRole.setEmployeeId(employeeId);
        userRole.setRoleId("r_base");
        userRole.setTenantCode(tenantCode);

        try {
            userDao.insert(user);
            userRoleDao.insert(userRole);
            log.info("外部用户首次登录，已落地影子账号 employeeId={} tenant={}", employeeId, tenantCode);
        } catch (DataIntegrityViolationException e) {
            // 并发首登：另一请求已完成落地
            if (userDao.getByEmployeeId(employeeId) == null) {
                throw e;
            }
            log.info("影子账号已存在，跳过创建 employeeId={}", employeeId);
        }
    }
}
