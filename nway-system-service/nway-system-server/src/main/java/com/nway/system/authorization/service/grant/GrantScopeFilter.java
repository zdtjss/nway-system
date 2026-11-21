package com.nway.system.authorization.service.grant;

import com.nway.system.authorization.service.decision.SubjectContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 授权作用域过滤（决策内核与策略包共用的唯一实现）。
 * <p>
 * 授权加载是<b>跨租户</b>的（{@code GrantCacheService} 按工号全局加载），因此任何消费方在使用授权前
 * 都必须先做两重收敛，否则会把 A 租户的授权带到 B 租户：
 * <ol>
 *   <li><b>授权归属租户</b>：授权所属租户必须等于主体当前有效租户；{@code public}/空视为平台级授权；</li>
 *   <li><b>资源租户作用域</b>：{@code TENANT} 资源仅归属租户可用；{@code PLATFORM}/{@code SHARED}
 *       对所有租户可用；未登记资源不做作用域限制（历史/通配授权）。</li>
 * </ol>
 * <p>
 * 有效租户 = 工作租户优先，其次归属租户。工作租户切换（{@code X-Work-Tenant}）时，
 * 授权视野收敛到该租户，防止管理员在 A 租户的角色被用于 B 租户。
 * <p>
 * <b>为什么必须抽成单一实现</b>：{@link AuthzDecisionService}（接口/数据判定）与
 * {@link AuthzBundleService}（对外策略包）曾是两份独立逻辑，策略包漏了这两重收敛，
 * 导致多租户管理员可从 bundle 拿到其他租户的授权。收敛点只能有一个。
 *
 * @author nway
 */
@Component
public class GrantScopeFilter {

    private final ResourceDao resourceDao;

    /** 资源作用域：按 app:resource 缓存 1 分钟（资源目录变更不频繁）。 */
    private final Cache<String, Optional<SysResource>> resourceCache = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public GrantScopeFilter(ResourceDao resourceDao) {
        this.resourceDao = resourceDao;
    }

    /**
     * 主体当前有效租户：工作租户优先，其次归属租户。
     */
    public String effectiveTenant(SubjectContext subject) {
        if (subject == null) {
            return null;
        }
        return StringUtils.defaultIfBlank(subject.workTenant(), subject.tenantCode());
    }

    /**
     * 判断一条授权是否适用于该主体当前租户（两重收敛的合并入口）。
     */
    public boolean applies(SysGrant grant, SubjectContext subject) {
        if (grant == null || subject == null) {
            return false;
        }
        String effectiveTenant = effectiveTenant(subject);
        return tenantApplies(grant, effectiveTenant) && resourceApplies(grant, effectiveTenant);
    }

    /**
     * 授权归属租户校验：授权所属租户必须等于工作租户；{@code public}/空视为平台级授权。
     */
    boolean tenantApplies(SysGrant grant, String effectiveTenant) {
        String grantTenant = grant.getTenantCode();
        if (StringUtils.isBlank(grantTenant) || TenantUtil.PUBLIC_TENANT_CODE.equals(grantTenant)) {
            return true;
        }
        return grantTenant.equals(effectiveTenant);
    }

    /**
     * 资源租户作用域校验：TENANT 资源仅归属租户可用；PLATFORM/SHARED 对所有租户可用。
     * 未登记资源（历史/通配授权）不做作用域限制，但仍受授权租户校验约束。
     */
    boolean resourceApplies(SysGrant grant, String effectiveTenant) {
        Optional<SysResource> cached = resourceCache.get(
                grant.getAppCode() + ":" + grant.getResourceCode(),
                key -> Optional.ofNullable(TenantUtil.callAsSystem(
                        () -> resourceDao.getByAppAndCode(grant.getAppCode(), grant.getResourceCode()))));
        if (cached == null || cached.isEmpty()) {
            return true;
        }
        SysResource resource = cached.get();
        if (SysResource.SCOPE_TENANT.equals(resource.getTenantScope())) {
            return StringUtils.isNotBlank(effectiveTenant)
                    && effectiveTenant.equals(resource.getOwnerTenantCode());
        }
        return true;
    }
}
