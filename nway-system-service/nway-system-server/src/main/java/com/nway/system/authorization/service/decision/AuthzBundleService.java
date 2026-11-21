package com.nway.system.authorization.service.decision;

import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.authorization.service.grant.GrantCacheService;
import com.nway.system.authorization.service.grant.GrantScopeFilter;
import com.nway.system.authorization.entity.SysGrant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对外开放的「策略包」：某用户在某应用下的最终权限 + 数据谓词 + 版本。
 * <p>
 * 外部应用（nway-system-authz-sdk）拉取后本地判定，避免每次请求远程 decide；
 * 版本单调递增，消费方按版本失效本地缓存。
 */
@Service
public class AuthzBundleService {

    private final GrantCacheService grantCacheService;
    private final AuthzDecisionService decisionService;
    private final AuthzVersionService versionService;
    private final SubjectContextResolver subjectContextResolver;
    private final GrantScopeFilter scopeFilter;

    public AuthzBundleService(GrantCacheService grantCacheService,
                              AuthzDecisionService decisionService,
                              AuthzVersionService versionService,
                              SubjectContextResolver subjectContextResolver,
                              GrantScopeFilter scopeFilter) {
        this.grantCacheService = grantCacheService;
        this.decisionService = decisionService;
        this.versionService = versionService;
        this.subjectContextResolver = subjectContextResolver;
        this.scopeFilter = scopeFilter;
    }

    public Bundle bundle(String employeeId, String appCode) {
        // 版本只读一次：入参与返回必须同一个版本，否则消费方会拿旧内容配新版本
        long version = versionService.current();
        // 必须使用真实主体上下文（含归属租户与部门），否则租户收敛会把全部授权过滤掉
        SubjectContext subject = subjectContextResolver.fromEmployeeId(employeeId);
        if (subject == null) {
            return new Bundle(version, appCode, List.of());
        }
        // 与决策内核保持一致：超管拥有全部资源与动作（SDK 支持 "*" 通配）
        if (subject.superAdmin()) {
            return new Bundle(version, appCode, List.of(new ResourceGrant(
                    "*", List.of(AuthzDecisionService.ALL_ACTIONS),
                    List.of(new AuthzDecisionService.DataPredicate(SysGrant.SCOPE_ALL, List.of())))));
        }

        List<SysGrant> grants = grantCacheService.effectiveGrants(employeeId);
        // 单遍合并：ALLOW 并集 / DENY 并集；最终 allow - deny。
        // 旧实现先合并再跑一遍 DENY，是为了修补“DENY 先于 ALLOW 出现”的顺序问题；
        // 分开累计后与顺序无关，少一遍遍历。
        Map<String, Set<String>> allow = new LinkedHashMap<>();
        Map<String, Set<String>> deny = new LinkedHashMap<>();
        for (SysGrant grant : grants) {
            if (!appCode.equals(grant.getAppCode())) {
                continue;
            }
            // 授权是跨租户加载的，对外下发前必须做租户/资源作用域收敛（与决策内核共用 GrantScopeFilter）
            if (!scopeFilter.applies(grant, subject)) {
                continue;
            }
            Map<String, Set<String>> target = grant.isAllow() ? allow : deny;
            target.computeIfAbsent(grant.getResourceCode(), k -> new LinkedHashSet<>())
                    .addAll(parse(grant.getActions()));
        }

        List<ResourceGrant> result = new ArrayList<>();
        allow.forEach((resource, actions) -> {
            Set<String> denied = deny.getOrDefault(resource, Set.of());
            // DENY "*" 表示整个资源被拒
            List<String> surviving = denied.contains(AuthzDecisionService.ALL_ACTIONS)
                    ? List.of()
                    : actions.stream().filter(a -> !denied.contains(a)).toList();
            if (surviving.isEmpty()) {
                return;
            }
            // 复用本次已加载的 grants 逐 action 判定（不再每个 action 重新拉一遍授权）
            List<String> allowed = new ArrayList<>();
            List<AuthzDecisionService.DataPredicate> predicates = List.of();
            for (String action : surviving) {
                AuthzDecisionService.Decision decision =
                        decisionService.decideWith(subject, grants, appCode, resource, action);
                if (!decision.allow()) {
                    continue;
                }
                allowed.add(action);
                if (predicates.isEmpty() && decision.dataPredicates() != null) {
                    predicates = decision.dataPredicates();
                }
            }
            if (!allowed.isEmpty()) {
                result.add(new ResourceGrant(resource, allowed, predicates));
            }
        });
        return new Bundle(version, appCode, result);
    }

    private static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = com.alibaba.fastjson2.JSON.parseArray(json, String.class);
            return list == null ? List.of() : list;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public record Bundle(long version, String appCode, List<ResourceGrant> resources) {
    }

    public record ResourceGrant(String resourceCode, List<String> actions,
                                List<AuthzDecisionService.DataPredicate> predicates) {
    }
}
