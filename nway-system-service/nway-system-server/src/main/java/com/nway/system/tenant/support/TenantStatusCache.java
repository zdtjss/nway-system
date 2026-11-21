package com.nway.system.tenant.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 租户可用性缓存（生命周期管控）。
 * <p>
 * 判断租户当前是否可用：存在 + {@code status='using'} + 未到期（{@code expire_date} 为空表示长期有效）。
 * <p>
 * 本地缓存 TTL 30 秒（与 {@link UserStatusCache} 的 L1 一致）；租户增删改时由
 * {@code TenantService} 主动驱逐，使变更最迟在一个 TTL 内全局收敛。多实例部署时，
 * 各实例各自 30 秒内收敛（如需更强一致性可后续接 Redis L2 + Pub/Sub 广播，同 UserStatusCache）。
 *
 * @author nway
 */
@Slf4j
@Component
public class TenantStatusCache {

    private static final String STATUS_USING = "using";

    private final Cache<String, Boolean> usableCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    @Autowired
    private TenantDao tenantDao;

    /**
     * 租户是否可用。空编码视为"无租户约束"（如超管），返回 true 不拦截。
     */
    public boolean isUsable(String tenantCode) {
        if (StringUtils.isBlank(tenantCode)) {
            return true;
        }
        Boolean cached = usableCache.getIfPresent(tenantCode);
        if (cached != null) {
            return cached;
        }
        boolean usable = TenantUtil.callAsSystem(() -> {
            Tenant tenant = tenantDao.getByCode(tenantCode);
            if (tenant == null) {
                return false;
            }
            if (!STATUS_USING.equals(tenant.getStatus())) {
                return false;
            }
            return tenant.getExpireDate() == null || !tenant.getExpireDate().isBefore(LocalDate.now());
        });
        usableCache.put(tenantCode, usable);
        return usable;
    }

    /** 租户状态变化（启停/到期/删除）后驱逐缓存。 */
    public void evict(String tenantCode) {
        if (StringUtils.isNotBlank(tenantCode)) {
            usableCache.invalidate(tenantCode);
        }
    }
}