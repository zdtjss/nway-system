package com.nway.system.authz.sdk.client;

import com.nway.system.authz.sdk.model.AuthzBundle;

import java.util.List;

/**
 * 策略包的本地判定（纯函数，便于契约测试）。
 * <p>
 * 语义与平台决策内核一致：精确资源优先于 {@code *}；动作 {@code *} 表示全部；
 * 未匹配 → 拒绝（fail-closed）。
 */
public final class AuthzEvaluator {

    private AuthzEvaluator() {
    }

    public static boolean allows(AuthzBundle bundle, String resourceCode, String action) {
        AuthzBundle.ResourceGrant grant = find(bundle, resourceCode);
        if (grant == null) {
            return false;
        }
        List<String> actions = grant.actions();
        return actions != null && (actions.contains(AuthzBundle.ALL_ACTIONS) || actions.contains(action));
    }

    public static List<AuthzBundle.DataPredicate> predicates(AuthzBundle bundle, String resourceCode) {
        AuthzBundle.ResourceGrant grant = find(bundle, resourceCode);
        return grant == null || grant.predicates() == null ? List.of() : grant.predicates();
    }

    /**
     * 精确资源优先，其次应用级通配。不能依赖列表顺序。
     */
    public static AuthzBundle.ResourceGrant find(AuthzBundle bundle, String resourceCode) {
        if (bundle == null || bundle.resources() == null) {
            return null;
        }
        AuthzBundle.ResourceGrant exact = bundle.resources().stream()
                .filter(r -> resourceCode.equals(r.resourceCode()))
                .findFirst()
                .orElse(null);
        if (exact != null) {
            return exact;
        }
        return bundle.resources().stream()
                .filter(r -> AuthzBundle.WILDCARD_RESOURCE.equals(r.resourceCode()))
                .findFirst()
                .orElse(null);
    }
}
