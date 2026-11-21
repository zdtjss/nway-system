package com.nway.system.tenant.service;

import com.nway.system.tenant.event.AdminGrantChangedEvent;
import com.nway.system.tenant.support.AdminGrantCacheManager;
import com.nway.system.tenant.web.model.AdminGrantDto;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.tenant.dao.AdminGrantDao;
import com.nway.system.tenant.entity.AdminTenant;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * 管理员授权服务。
 * <p>
 * 仅超级管理员可调用（调用方需在 Controller 层校验 {@code isSuperAdmin}）。
 * 管理员管理范围纯按授权租户划分（业务大类从属于租户，无需独立授权）。
 * </p>
 */
@Service
public class AdminGrantService {

    @Autowired
    private AdminGrantDao adminGrantDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private AdminGrantCacheManager adminGrantCacheManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 任命管理员并设置其可管理的租户列表（替换式）。
     *
     * @param employeeId  工号
     * @param tenantCodes 可管理租户编码列表（不能为空，至少授权一个租户）
     */
    @Transactional(rollbackFor = Exception.class)
    public void grant(String employeeId, List<String> tenantCodes) {
        if (StringUtils.isBlank(employeeId)) {
            throw new NwaySystemException("工号不能为空");
        }
        User user = userDao.getByEmployeeId(employeeId);
        if (user == null) {
            throw new NwaySystemException("用户不存在");
        }
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            throw new NwaySystemException("超级管理员无需授予管理员身份");
        }
        if (CollectionUtils.isEmpty(tenantCodes)) {
            throw new NwaySystemException("至少授权一个可管理的租户");
        }

        // 任命为管理员
        userDao.updateIsAdminByEmployeeId(employeeId, true);

        // 整体替换授权租户（先清后写）
        adminGrantDao.deleteTenantByEmployee(employeeId);
        for (String tenantCode : tenantCodes) {
            if (StringUtils.isBlank(tenantCode)) {
                continue;
            }
            AdminTenant tenant = new AdminTenant();
            tenant.setEmployeeId(employeeId);
            tenant.setTenantCode(tenantCode);
            adminGrantDao.insertTenant(tenant);
        }

        adminGrantCacheManager.evict(employeeId);
        eventPublisher.publishEvent(new AdminGrantChangedEvent(this, Map.of(
                "employeeId", employeeId, "action", "grant",
                "tenantCodes", tenantCodes)));
    }

    /**
     * 撤销管理员：清除身份与全部授权。
     *
     * @param employeeId 工号
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String employeeId) {
        if (StringUtils.isBlank(employeeId)) {
            throw new NwaySystemException("工号不能为空");
        }
        User user = userDao.getByEmployeeId(employeeId);
        if (user != null && Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            throw new NwaySystemException("超级管理员不可被撤销管理员身份");
        }
        userDao.updateIsAdminByEmployeeId(employeeId, false);
        adminGrantDao.deleteTenantByEmployee(employeeId);
        adminGrantCacheManager.evict(employeeId);
        eventPublisher.publishEvent(new AdminGrantChangedEvent(this, Map.of(
                "employeeId", employeeId, "action", "revoke")));
    }

    /**
     * 查询指定用户的管理员授权（走缓存）。
     */
    public AdminGrantDto queryByEmployee(String employeeId) {
        if (StringUtils.isBlank(employeeId)) {
            throw new NwaySystemException("工号不能为空");
        }
        return adminGrantCacheManager.get(employeeId);
    }

    /**
     * 查询所有平台管理员及其授权范围。
     */
    public List<AdminGrantDto> listAdmins() {
        List<User> admins = userDao.queryAdmins();
        return admins.stream().map(u -> adminGrantCacheManager.get(u.getEmployeeId())).toList();
    }
}
