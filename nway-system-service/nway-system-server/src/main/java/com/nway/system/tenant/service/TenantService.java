package com.nway.system.tenant.service;

import com.nway.spring.jdbc.pagination.Page;
import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.identity.service.TokenVersionPubSubService;
import com.nway.system.identity.service.UserStatusCache;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.tenant.domain.TenantCodePolicy;
import com.nway.system.tenant.support.TenantStatusCache;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import com.nway.system.tenant.web.model.TenantQuery;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private TenantStatusCache tenantStatusCache;

    @Autowired
    private AuthzVersionService authzVersionService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private UserStatusCache userStatusCache;

    @Autowired
    private TokenVersionPubSubService tokenVersionPubSubService;

    @Transactional(rollbackFor = Exception.class)
    public void save(TenantDto tenantDto) {
        if (!TenantUtil.isSuperAdmin()) {
            throw new NwaySystemException("仅超级管理员可操作租户");
        }
        // 租户编码单一事实源（contracts/host-tenant.json）：2-20位，字母开头
        TenantCodePolicy.requireValid(tenantDto.getTenantCode());
        if (tenantDao.countByCode(tenantDto.getTenantCode(), tenantDto.getId()) > 0) {
            throw new NwaySystemException("租户编码已存在");
        }
        if (tenantDao.countByName(tenantDto.getTenantName(), tenantDto.getId()) > 0) {
            throw new NwaySystemException("租户名称已存在");
        }
        if (StringUtils.isBlank(tenantDto.getId())) {
            Tenant tenant = ConverterUtil.convert(tenantDto, Tenant.class);
            if (StringUtils.isBlank(tenant.getStatus())) {
                tenant.setStatus("using");
            }
            // 手动填充审计字段（Tenant 不继承 BaseEntity，无自动策略）
            fillAuditOnInsert(tenant);
            tenantDao.insert(tenant);
        } else {
            Tenant existing = tenantDao.getById(tenantDto.getId());
            if (existing == null) {
                throw new NwaySystemException("租户不存在");
            }
            // 租户编码不可修改：改码会导致其下资源成为孤儿（default/public 共享与兜底语义依赖固定编码）
            if (!existing.getTenantCode().equals(tenantDto.getTenantCode())) {
                throw new NwaySystemException("租户编码不可修改");
            }
            Tenant tenant = ConverterUtil.convert(tenantDto, Tenant.class);
            fillAuditOnUpdate(tenant);
            tenantDao.updateById(tenant);
            // 运行时禁用/到期：立即失效该租户全部令牌与授权缓存（不只依赖后续请求拦截）
            if (isUsable(existing) && !isUsable(tenant)) {
                invalidateTenantRuntime(tenant.getTenantCode());
            }
        }
        // 启用/禁用/改到期日等都会改变可用性，驱逐缓存使变更尽快生效
        tenantStatusCache.evict(tenantDto.getTenantCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteById(String id) {
        if (!TenantUtil.isSuperAdmin()) {
            throw new NwaySystemException("仅超级管理员可操作租户");
        }
        Tenant tenant = tenantDao.getById(id);
        if (tenant == null) {
            throw new NwaySystemException("租户不存在");
        }
        if (TenantUtil.DEFAULT_TENANT_CODE.equals(tenant.getTenantCode())
                || TenantUtil.PUBLIC_TENANT_CODE.equals(tenant.getTenantCode())) {
            throw new NwaySystemException("内置租户不可删除");
        }
        tenantDao.deleteById(id);
        tenantStatusCache.evict(tenant.getTenantCode());
        invalidateTenantRuntime(tenant.getTenantCode());
    }

    /**
     * 租户不可用时立即失效：递增授权版本使权限缓存整体失效，并自增该租户全部用户的
     * 令牌版本（旧 Token 不可续期、不可用）。
     */
    private void invalidateTenantRuntime(String tenantCode) {
        authzVersionService.bump();
        List<User> users = TenantUtil.callAsSystem(() -> userDao.queryByTenantCode(tenantCode));
        for (User user : users) {
            TenantUtil.callAsSystem(() -> {
                userDao.incrementTokenVersion(user.getEmployeeId());
                return null;
            });
            userStatusCache.evictTokenVersion(user.getEmployeeId());
            tokenVersionPubSubService.publishVersionChange(
                    user.getEmployeeId(), TokenVersionPubSubService.ACTION_LOGOUT);
        }
        log.warn("租户 {} 已不可用：授权版本已递增，{} 个用户的令牌已失效", tenantCode, users.size());
    }

    private static boolean isUsable(Tenant tenant) {
        return "using".equalsIgnoreCase(tenant.getStatus())
                && (tenant.getExpireDate() == null || !tenant.getExpireDate().isBefore(LocalDate.now()));
    }

    public PageVo<TenantDto> queryPage(TenantQuery query) {
        Page<Tenant> page = tenantDao.queryPage(query);
        return ConverterUtil.convert(page, TenantDto.class);
    }

    public TenantDto getById(String id) {
        return ConverterUtil.convert(tenantDao.getById(id), TenantDto.class);
    }

    public TenantDto getByCode(String tenantCode) {
        return ConverterUtil.convert(tenantDao.getByCode(tenantCode), TenantDto.class);
    }

    public List<TenantDto> queryAll() {
        List<Tenant> list = tenantDao.queryAll();
        return ConverterUtil.convert(list, TenantDto.class);
    }

    /**
     * 查询当前用户可管理的租户（角色归属/复制目标等业务下拉用）。
     * <p>
     * 与 {@link #queryAll()} 的区别：后者仅超管可用（租户管理权限），业务页面不能依赖它；
     * 本方法按 {@link TenantUtil#currentManageableTenantCodes()} 收敛——超管返回全部启用租户，
     * 管理员返回授权租户，普通用户返回本租户，且只保留启用状态。
     */
    public List<TenantDto> queryManageable() {
        if (TenantUtil.isSuperAdmin()) {
            return queryAll();
        }
        List<String> codes = TenantUtil.currentManageableTenantCodes();
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<Tenant> tenants = tenantDao.queryByTenantCode(codes).stream()
                .filter(tenant -> "using".equalsIgnoreCase(tenant.getStatus()))
                .toList();
        return ConverterUtil.convert(tenants, TenantDto.class);
    }

    /**
     * 查询当前管理员可管理的租户列表（工作租户切换用）。
     * <p>
     * 超管返回全部启用租户；管理员返回授权租户详情；普通用户返回空列表。
     */
    public List<TenantDto> queryMyAdminTenants() {
        if (TenantUtil.isSuperAdmin()) {
            return queryAll();
        }
        if (!TenantUtil.isAdmin()) {
            return List.of();
        }
        List<String> codes = TenantUtil.currentAdminTenantCodes();
        if (codes.isEmpty()) {
            return List.of();
        }
        List<Tenant> tenants = tenantDao.queryByTenantCode(codes);
        return ConverterUtil.convert(tenants, TenantDto.class);
    }

    private void fillAuditOnInsert(Tenant tenant) {
        LoginUser user = LoginUserContext.current();
        if (user != null) {
            tenant.setCreatorId(user.getEmployeeId());
            tenant.setCreatorName(user.getRealName());
            tenant.setModifierId(user.getEmployeeId());
            tenant.setModifierName(user.getRealName());
        }
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setModifiedTime(LocalDateTime.now());
        tenant.setIsDeleted(Boolean.FALSE);
    }

    private void fillAuditOnUpdate(Tenant tenant) {
        LoginUser user = LoginUserContext.current();
        if (user != null) {
            tenant.setModifierId(user.getEmployeeId());
            tenant.setModifierName(user.getRealName());
        }
        tenant.setModifiedTime(LocalDateTime.now());
    }
}
