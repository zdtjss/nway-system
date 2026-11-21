package com.nway.system.tenant.service;

import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 租户名称批量解析器。
 * <p>
 * 供各资源列表查询后批量填充 tenantName 使用，避免各 Service 重复实现。
 *
 * @author nway
 */
@Component
@RequiredArgsConstructor
public class TenantNameResolver {

    private final TenantDao tenantDao;

    /**
     * 批量解析租户编码对应的租户名称。
     *
     * @param tenantCodes 租户编码集合（可含空值，自动去重过滤）
     * @return tenantCode -> tenantName 映射，入参为空时返回空 Map
     */
    public Map<String, String> resolveNames(Collection<String> tenantCodes) {
        if (tenantCodes == null || tenantCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> codes = new HashSet<>();
        for (String code : tenantCodes) {
            if (StringUtils.isNotBlank(code)) {
                codes.add(code);
            }
        }
        if (codes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> nameMap = new HashMap<>();
        for (Tenant t : tenantDao.queryByTenantCode(codes)) {
            nameMap.put(t.getTenantCode(), t.getTenantName());
        }
        return nameMap;
    }
}
