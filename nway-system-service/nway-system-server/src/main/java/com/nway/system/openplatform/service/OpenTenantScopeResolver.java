package com.nway.system.openplatform.service;

import com.nway.system.tenant.support.AdminGrantCacheManager;
import com.nway.system.tenant.web.model.AdminGrantDto;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 开放平台"可管理租户"解析。
 * <p>
 * 与 {@link TenantUtil#currentManageableTenantCodes()}（管理端登录上下文）同构，输入换成开放端的代理用户：
 * <ul>
 *   <li>超管 → {@code null}（不限制，等价"全部启用租户"）</li>
 *   <li>平台管理员 → {所属租户} ∪ {@code sys_admin_tenant} 授权租户</li>
 *   <li>普通用户 → {所属租户}</li>
 *   <li>用户不存在 → 空集合（fail-closed）</li>
 * </ul>
 * 所有数据库读取均以系统身份执行（{@link TenantUtil#callAsSystem}），
 * 否则开放端的 token 作用域隔离会把"他租户的授权/用户信息"静默过滤，得出错误的可管理集合。
 *
 * @author nway
 */
@Slf4j
@Service
public class OpenTenantScopeResolver {

    /** 启用的租户状态（与 TenantDao#queryAll 一致） */
    private static final String TENANT_STATUS_USING = "using";

    @Autowired
    private UserDao userDao;

    @Autowired
    private AdminGrantCacheManager adminGrantCacheManager;

    @Autowired
    private TenantDao tenantDao;

    /**
     * 代理用户可管理的租户编码集合。
     *
     * @param employeeId 代理用户工号
     * @return {@code null} = 不限制（超管）；否则为可管理租户编码（可能为空集合，表示无任何可管理租户）
     */
    public List<String> manageableTenants(String employeeId) {
        return TenantUtil.callAsSystem(() -> {
            User user = StringUtils.isBlank(employeeId) ? null : userDao.getByEmployeeId(employeeId);
            if (user == null) {
                return List.<String>of();
            }
            if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
                return null;
            }
            LinkedHashSet<String> tenants = new LinkedHashSet<>();
            if (StringUtils.isNotBlank(user.getTenantCode())) {
                tenants.add(user.getTenantCode());
            }
            AdminGrantDto grant = adminGrantCacheManager.get(employeeId);
            if (grant.isAdmin() && grant.getTenantCodes() != null) {
                for (String code : grant.getTenantCodes()) {
                    if (StringUtils.isNotBlank(code)) {
                        tenants.add(code);
                    }
                }
            }
            return new ArrayList<>(tenants);
        });
    }

    /**
     * 目标租户是否在代理用户的可管理范围内（超管 = 全部启用租户）。
     *
     * @param employeeId 代理用户工号
     * @param tenantCode 目标租户编码
     */
    public boolean canManage(String employeeId, String tenantCode) {
        if (StringUtils.isBlank(tenantCode)) {
            return false;
        }
        List<String> tenants = manageableTenants(employeeId);
        if (tenants == null) {
            // 超管：仅要求目标租户存在且启用（避免把不存在的租户写进 token 作用域）
            return enabledTenants().stream().anyMatch(t -> tenantCode.equals(t.getTenantCode()));
        }
        return tenants.contains(tenantCode);
    }

    /**
     * 可管理租户明细（含租户名称），供开放端租户切换/下拉展示。
     * <p>
     * 超管 = 全部启用租户；管理员 = 授权集合中的启用租户；普通用户 = 所属租户（启用时）。
     *
     * @param employeeId 代理用户工号
     */
    public List<TenantDto> manageableTenantDetails(String employeeId) {
        List<String> manageable = manageableTenants(employeeId);
        List<Tenant> tenants = manageable == null ? enabledTenants() : selectEnabled(manageable);
        List<TenantDto> result = new ArrayList<>(tenants.size());
        for (Tenant tenant : tenants) {
            TenantDto dto = new TenantDto();
            dto.setId(tenant.getId());
            dto.setTenantCode(tenant.getTenantCode());
            dto.setTenantName(tenant.getTenantName());
            dto.setStatus(tenant.getStatus());
            result.add(dto);
        }
        return result;
    }

    /** 全部启用租户（sys_tenant 不参与租户隔离，直接查询）。 */
    private List<Tenant> enabledTenants() {
        return tenantDao.queryAll();
    }

    private List<Tenant> selectEnabled(List<String> tenantCodes) {
        if (tenantCodes.isEmpty()) {
            return List.of();
        }
        return tenantDao.queryByTenantCode(tenantCodes).stream()
                .filter(t -> TENANT_STATUS_USING.equals(t.getStatus()))
                .toList();
    }
}