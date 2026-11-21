package com.nway.system.authorization.service.grant;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.authorization.dao.GrantDao;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.identity.dao.SysUserPositionDao;
import com.nway.system.identity.entity.SysUserPosition;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户授权加载与缓存。
 * <p>
 * 缓存以<b>全局授权版本</b>为准整体失效：版本变化（授权写入）后首个请求触发清空，
 * 不再按角色枚举用户逐个驱逐。缓存 key 为工号，value 为该用户的有效授权
 * （USER + 角色 + 岗位，已过滤状态/时间窗）。
 */
@Service
public class GrantCacheService {

    private static final Logger log = LoggerFactory.getLogger(GrantCacheService.class);

    private final GrantDao grantDao;
    private final UserRoleDao userRoleDao;
    private final SysUserPositionDao userPositionDao;
    private final AuthzVersionService versionService;

    private final com.github.benmanes.caffeine.cache.Cache<String, List<SysGrant>> cache =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(Duration.ofMinutes(1))
                    .build();

    private volatile long knownVersion = Long.MIN_VALUE;

    public GrantCacheService(GrantDao grantDao, UserRoleDao userRoleDao,
                             SysUserPositionDao userPositionDao, AuthzVersionService versionService) {
        this.grantDao = grantDao;
        this.userRoleDao = userRoleDao;
        this.userPositionDao = userPositionDao;
        this.versionService = versionService;
    }

    public List<SysGrant> effectiveGrants(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return List.of();
        }
        refreshIfVersionChanged();
        return cache.get(employeeId, this::load);
    }

    private void refreshIfVersionChanged() {
        long version = versionService.current();
        if (version >= 0 && version != knownVersion) {
            cache.invalidateAll();
            knownVersion = version;
        }
    }

    /** 显式失效（授权写入后立即调用，避免等待版本缓存 2 秒）。 */
    public void invalidateAll() {
        cache.invalidateAll();
        knownVersion = Long.MIN_VALUE;
    }

    private List<SysGrant> load(String employeeId) {
        LocalDateTime now = LocalDateTime.now();
        List<SysGrant> grants = new ArrayList<>();
        // 缓存 key 为全局工号，加载必须显式跨租户，避免被调用方租户上下文过滤
        List<SysGrant> all = TenantUtil.callAsSystem(() -> {
            List<SysGrant> result = new ArrayList<>(grantDao.queryByUser(employeeId));
            List<String> roleIds = userRoleDao.queryByUser(List.of(employeeId)).stream() .map(UserRole::getRoleId).distinct().collect(Collectors.toList());
            result.addAll(grantDao.queryBySubjects("ROLE", roleIds));
            List<String> positionIds = userPositionDao.queryByEmployee(employeeId).stream().map(SysUserPosition::getPositionId).distinct().collect(Collectors.toList());
            result.addAll(grantDao.queryBySubjects("POSITION", positionIds));
            return result;
        });
        for (SysGrant grant : all) {
            if (grant != null && grant.isEffectiveAt(now)) {
                grants.add(grant);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("加载用户授权 employeeId={}, 有效 {} 条", employeeId, grants.size());
        }
        return List.copyOf(grants);
    }
}
