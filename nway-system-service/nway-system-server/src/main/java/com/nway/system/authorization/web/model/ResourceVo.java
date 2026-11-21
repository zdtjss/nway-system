package com.nway.system.authorization.web.model;

import java.util.List;

/**
 * 资源视图（含动作）。
 */
public record ResourceVo(
        String id,
        String appCode,
        String resourceCode,
        String resourceType,
        String name,
        String description,
        String tenantScope,
        String ownerTenantCode,
        String status,
        List<ActionVo> actions
) {
    public record ActionVo(String action, String label, String riskLevel) {
    }
}
