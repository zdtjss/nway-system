package com.nway.system.authorization.service.decision;

import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.authorization.service.grant.GrantCacheService;
import com.nway.system.authorization.service.grant.GrantScopeFilter;
import com.alibaba.fastjson2.JSON;
import com.nway.system.authorization.entity.SysGrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 唯一决策内核（PDP）。
 * <p>
 * 语义（与 {@code contracts/authz-semantics.md} 对齐）：
 * <ol>
 *   <li>超管 → ALLOW(ALL)；</li>
 *   <li>取主体有效授权（状态/时间窗已过滤）；</li>
 *   <li>任一 DENY 命中 → DENY，理由为命中的授权 id；</li>
 *   <li>任一 ALLOW 命中 → ALLOW，数据谓词取各命中授权的并集；</li>
 *   <li>否则 DENY(no-grant)。</li>
 * </ol>
 * 每次决策产出 {@code decisionId}、命中授权与授权版本，供 403 诊断与审计关联。
 */
@Service
public class AuthzDecisionService {

    private static final Logger log = LoggerFactory.getLogger(AuthzDecisionService.class);

    public static final String ALL_ACTIONS = "*";

    private final GrantCacheService grantCacheService;
    private final AuthzVersionService versionService;
    private final GrantScopeFilter scopeFilter;

    public AuthzDecisionService(GrantCacheService grantCacheService, AuthzVersionService versionService,
                                GrantScopeFilter scopeFilter) {
        this.grantCacheService = grantCacheService;
        this.versionService = versionService;
        this.scopeFilter = scopeFilter;
    }

    /**
     * 数据谓词：PEP 只翻译，不再自行推导。
     * <ul>
     *   <li>{@code ALL}：不限制；</li>
     *   <li>{@code SELF}：仅本人（值=工号）；</li>
     *   <li>{@code DEPT}/{@code DEPT_TREE}：值=主体部门编码（子树由消费方按组织路径展开）；</li>
     *   <li>{@code DEPT_LIST}：值=显式部门编码列表；</li>
     *   <li>{@code NONE}：无数据。</li>
     * </ul>
     */
    public record DataPredicate(String kind, List<String> values) {
    }

    public record Decision(boolean allow, String reason, String decisionId,
                           List<DataPredicate> dataPredicates,
                           List<String> matchedGrantIds, long version) {
    }

    public Decision decide(SubjectContext subject, String appCode, String resourceCode, String action) {
        if (subject == null) {
            return new Decision(false, "no-subject", newDecisionId(), List.of(), List.of(),
                    versionService.current());
        }
        return decideWith(subject, grantCacheService.effectiveGrants(subject.employeeId()),
                appCode, resourceCode, action);
    }

    /**
     * 用调用方已加载的授权做判定（bundle 等批量场景复用，避免“每个 action 重新加载一遍”）。
     * <p>
     * 语义与 {@link #decide} 完全一致：租户收敛 → resource/action 匹配 → DENY 优先 →
     * ALLOW 并集取谓词。
     */
    public Decision decideWith(SubjectContext subject, List<SysGrant> grants,
                               String appCode, String resourceCode, String action) {
        long version = versionService.current();
        String decisionId = newDecisionId();
        if (subject == null) {
            return new Decision(false, "no-subject", decisionId, List.of(), List.of(), version);
        }
        if (subject.superAdmin()) {
            return new Decision(true, "super-admin", decisionId,
                    List.of(new DataPredicate(SysGrant.SCOPE_ALL, List.of())), List.of(), version);
        }
        if (grants == null) {
            grants = List.of();
        }
        // 租户/资源作用域收敛复用 GrantScopeFilter（与策略包 bundle 同一实现）：
        // 工作租户优先，切换到某租户办公时授权视野收敛到该租户 + SHARED/PLATFORM 资源。
        List<SysGrant> matched = new ArrayList<>();
        for (SysGrant grant : grants) {
            if (!scopeFilter.applies(grant, subject)) {
                continue;
            }
            if (resourceMatches(grant, appCode, resourceCode) && actionMatches(grant, action)) {
                matched.add(grant);
            }
        }
        List<SysGrant> denies = matched.stream().filter(g -> !g.isAllow()).toList();
        if (!denies.isEmpty()) {
            List<String> ids = denies.stream().map(SysGrant::getId).toList();
            log.debug("授权拒绝（DENY）employee={}, {}:{}, action={}, grants={}", subject.employeeId(), appCode, resourceCode, action, ids);
            return new Decision(false, "deny-grant", decisionId, List.of(), ids, version);
        }
        List<SysGrant> allows = matched.stream().filter(SysGrant::isAllow).toList();
        if (allows.isEmpty()) {
            return new Decision(false, "no-grant", decisionId, List.of(), List.of(), version);
        }
        return new Decision(true, "allow-grant", decisionId,
                predicates(allows, subject), allows.stream().map(SysGrant::getId).toList(), version);
    }

    /** 便捷判定：仅关心是否放行。 */
    public boolean allows(SubjectContext subject, String appCode, String resourceCode, String action) {
        return decide(subject, appCode, resourceCode, action).allow();
    }

    /**
     * 批量判定项。{@code action} 为空表示“该资源上任意动作放行即可”（菜单可见性场景）。
     */
    public record Check(String key, String appCode, String resourceCode, String action) {
    }

    /**
     * 批量判定：<b>只加载一次授权</b>，逐项复用；返回放行的 key 集合。
     * <p>
     * 菜单曾经按“资源在策略里即可见”推导，逐条菜单都会拉一次合并策略；
     * 改成批量后一次加载、N 次内存判定，同时与接口/数据权限共用 DENY 优先语义。
     */
    public Set<String> decideBatch(SubjectContext subject, List<Check> checks) {
        if (subject == null || checks == null || checks.isEmpty()) {
            return Set.of();
        }
        List<SysGrant> grants = subject.superAdmin()
                ? List.of()
                : grantCacheService.effectiveGrants(subject.employeeId());
        Set<String> allowed = new LinkedHashSet<>();
        for (Check check : checks) {
            boolean ok = check.action() == null || check.action().isBlank()
                    ? allowsAnyWith(subject, grants, check.appCode(), check.resourceCode())
                    : decideWith(subject, grants, check.appCode(), check.resourceCode(), check.action()).allow();
            if (ok) {
                allowed.add(check.key());
            }
        }
        return allowed;
    }

    /**
     * 该资源上是否至少有一个动作放行（DENY 优先）。
     * <p>
     * 用于菜单 PAGE 可见性：与旧的“资源在策略中存在”语义最接近，
     * 但会正确识别 DENY 通配封禁。
     */
    public boolean allowsAnyWith(SubjectContext subject, List<SysGrant> grants,
                                 String appCode, String resourceCode) {
        if (subject == null) {
            return false;
        }
        if (subject.superAdmin()) {
            return true;
        }
        if (grants == null) {
            return false;
        }
        boolean allowed = false;
        for (SysGrant grant : grants) {
            if (!scopeFilter.applies(grant, subject)) {
                continue;
            }
            if (!resourceMatches(grant, appCode, resourceCode)) {
                continue;
            }
            if (!grant.isAllow()) {
                return false;
            }
            allowed = true;
        }
        return allowed;
    }

    private static boolean resourceMatches(SysGrant grant, String appCode, String resourceCode) {
        if (!appCode.equals(grant.getAppCode())) {
            return false;
        }
        String granted = grant.getResourceCode();
        if (granted == null) {
            return false;
        }
        // * 表示该应用全部资源；ai:* 这类前缀通配只覆盖同前缀资源
        if (ALL_ACTIONS.equals(granted) || granted.equals(resourceCode)) {
            return true;
        }
        return granted.endsWith(ALL_ACTIONS) && granted.length() > 1
                && resourceCode.startsWith(granted.substring(0, granted.length() - 1));
    }

    private static boolean actionMatches(SysGrant grant, String action) {
        Set<String> actions = parseActions(grant.getActions());
        return actions.contains(ALL_ACTIONS) || actions.contains(action);
    }

    private static Set<String> parseActions(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = JSON.parseArray(json, String.class);
            return list == null ? Set.of() : new LinkedHashSet<>(list);
        } catch (RuntimeException e) {
            // 动作 JSON 损坏：按无动作处理（fail-closed），并留痕便于修复
            log.warn("授权动作 JSON 解析失败，按无动作处理：{}", json);
            return Set.of();
        }
    }

    /**
     * 合并数据谓词：任一条 ALL 即不限制；否则按 kind 归并（同 kind 值取并集）。
     */
    private static List<DataPredicate> predicates(List<SysGrant> grants, SubjectContext subject) {
        Map<String, Set<String>> merged = new LinkedHashMap<>();
        for (SysGrant grant : grants) {
            String scope = grant.getDataScope() == null ? SysGrant.SCOPE_SELF : grant.getDataScope();
            switch (scope) {
                case SysGrant.SCOPE_ALL -> {
                    return List.of(new DataPredicate(SysGrant.SCOPE_ALL, List.of()));
                }
                case SysGrant.SCOPE_SELF ->
                        merged.computeIfAbsent(SysGrant.SCOPE_SELF, k -> new LinkedHashSet<>())
                                .add(subject.employeeId());
                case SysGrant.SCOPE_DEPT, SysGrant.SCOPE_DEPT_TREE ->
                        merged.computeIfAbsent(scope, k -> new LinkedHashSet<>())
                                .addAll(subject.deptCodes());
                case SysGrant.SCOPE_DEPT_LIST ->
                        merged.computeIfAbsent(SysGrant.SCOPE_DEPT_LIST, k -> new LinkedHashSet<>())
                                .addAll(parseActions(grant.getScopeDeptIds()));
                default -> {
                    // NONE / 未知范围：不产生数据可见性
                }
            }
        }
        List<DataPredicate> result = new ArrayList<>();
        merged.forEach((kind, values) -> {
            if (!values.isEmpty() || SysGrant.SCOPE_ALL.equals(kind)) {
                result.add(new DataPredicate(kind, List.copyOf(values)));
            }
        });
        return result;
    }

    private static String newDecisionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
