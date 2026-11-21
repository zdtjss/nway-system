package com.nway.system.authz.sdk.model;

import java.util.List;

/**
 * 资源权限策略（从平台 /open/permission/policies 接口获取的单资源视图）。
 */
public record ResourcePolicy(
        String appCode,
        String resourceCode,
        /** null = 全部操作 */
        List<String> actions,
        String dataScope,
        List<String> deptIds,
        List<String> deptCodes
) {
    /**
     * 判断是否允许指定操作
     */
    public boolean hasAction(String action) {
        if (actions == null) return true;
        return actions.contains(action);
    }

    /**
     * 判断是否有任意操作权限
     */
    public boolean hasAnyAction() {
        return actions == null || !actions.isEmpty();
    }
}
