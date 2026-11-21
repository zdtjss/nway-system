package com.nway.system.authorization.service.policy;

import com.alibaba.fastjson2.JSON;
import com.nway.system.authorization.domain.MergedPolicy;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.authorization.entity.PermissionPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限策略合并器。
 * <p>
 * 将用户通过角色/岗位/个人获得的多条 PermissionPolicy 合并为最终的 MergedPolicy 集合。
 * <p>
 * 合并规则：
 * <ol>
 *   <li>按 appCode:resourceCode 分组</li>
 *   <li>USER 级策略直接覆盖 ROLE/POSITION 级（不做合并）</li>
 *   <li>ROLE + POSITION 级策略合并：actions 取并集，dataScope 取最宽</li>
 *   <li>dataScope 优先级：ALL > DEPT_WITH_SUB > DEPT > CUSTOM > EXPRESSION > SELF</li>
 *   <li>CUSTOM 类型多条策略的 scopeDeptIds 取并集</li>
 * </ol>
 */
@Component
public class PolicyMerger {

    /** 数据范围优先级（值越大范围越宽） */
    private static final Map<String, Integer> SCOPE_PRIORITY = Map.of(
            "ALL", 100,
            "DEPT_WITH_SUB", 80,
            "DEPT", 60,
            "CUSTOM", 40,
            "EXPRESSION", 20,
            "SELF", 10
    );

    @Autowired
    private SysDeptDao sysDeptDao;

    /**
     * 合并多条策略为用户最终权限视图。
     *
     * @param userPolicies     USER 级策略
     * @param rolePolicies     ROLE 级策略
     * @param positionPolicies POSITION 级策略
     * @return appCode:resourceCode → MergedPolicy 映射
     */
    public Map<String, MergedPolicy> merge(List<PermissionPolicy> userPolicies,
                                           List<PermissionPolicy> rolePolicies,
                                           List<PermissionPolicy> positionPolicies) {
        // 按 key(appCode:resourceCode) 分组
        Map<String, List<PermissionPolicy>> userGroup = groupByKey(userPolicies);
        Map<String, List<PermissionPolicy>> roleGroup = groupByKey(rolePolicies);
        Map<String, List<PermissionPolicy>> positionGroup = groupByKey(positionPolicies);

        // 收集所有出现过的 key
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(userGroup.keySet());
        allKeys.addAll(roleGroup.keySet());
        allKeys.addAll(positionGroup.keySet());

        Map<String, MergedPolicy> result = new LinkedHashMap<>();
        for (String key : allKeys) {
            List<PermissionPolicy> userLevel = userGroup.getOrDefault(key, List.of());
            List<PermissionPolicy> roleLevel = roleGroup.getOrDefault(key, List.of());
            List<PermissionPolicy> positionLevel = positionGroup.getOrDefault(key, List.of());

            MergedPolicy merged;
            if (!userLevel.isEmpty()) {
                // USER 级存在则直接覆盖
                merged = mergeWithinLevel(userLevel);
                merged.setSourceDescriptions(List.of("用户个人策略"));
            } else {
                // 合并 ROLE + POSITION 级
                List<PermissionPolicy> combined = new ArrayList<>();
                combined.addAll(roleLevel);
                combined.addAll(positionLevel);
                merged = mergeWithinLevel(combined);
                merged.setSourceDescriptions(buildSourceDescriptions(roleLevel, positionLevel));
            }

            // 填充 appCode/resourceCode
            String[] parts = key.split(":", 2);
            merged.setAppCode(parts[0]);
            merged.setResourceCode(parts.length > 1 ? parts[1] : "*");

            // 解析 deptCodes（用于 DEPT_WITH_SUB 的 LIKE 前缀匹配）
            if ("DEPT_WITH_SUB".equals(merged.getDataScope()) && !CollectionUtils.isEmpty(merged.getDeptIds())) {
                List<SysDept> depts = sysDeptDao.queryByIds(merged.getDeptIds());
                merged.setDeptCodes(depts.stream().map(SysDept::getDeptCode).filter(Objects::nonNull).collect(Collectors.toList()));
            }

            result.put(key, merged);
        }

        return result;
    }

    /**
     * 同级别策略合并：actions 取并集，dataScope 取最宽
     */
    private MergedPolicy mergeWithinLevel(List<PermissionPolicy> policies) {
        MergedPolicy merged = new MergedPolicy();
        if (policies.isEmpty()) {
            merged.setActions(Set.of());
            merged.setDataScope("SELF");
            return merged;
        }

        // === actions 合并（并集） ===
        Set<String> mergedActions = new HashSet<>();
        boolean allActions = false;
        for (PermissionPolicy p : policies) {
            if (p.getActions() == null) {
                allActions = true;
                break;
            }
            List<String> actionList = parseActions(p.getActions());
            mergedActions.addAll(actionList);
        }
        merged.setActions(allActions ? null : mergedActions);

        // === dataScope 合并（取最宽） ===
        String widestScope = "SELF";
        int widestPriority = 0;
        Set<String> mergedDeptIds = new LinkedHashSet<>();

        for (PermissionPolicy p : policies) {
            String scope = p.getDataScope();
            if (StringUtils.isBlank(scope)) {
                scope = "SELF";
            }
            int priority = SCOPE_PRIORITY.getOrDefault(scope.toUpperCase(), 0);
            if (priority > widestPriority) {
                widestPriority = priority;
                widestScope = scope.toUpperCase();
            }
            // CUSTOM 类型合并 deptIds
            if ("CUSTOM".equalsIgnoreCase(scope) && StringUtils.isNotBlank(p.getScopeDeptIds())) {
                List<String> deptIds = JSON.parseArray(p.getScopeDeptIds(), String.class);
                if (deptIds != null) {
                    mergedDeptIds.addAll(deptIds);
                }
            }
            // DEPT / DEPT_WITH_SUB 也收集 deptIds
            if (("DEPT".equalsIgnoreCase(scope) || "DEPT_WITH_SUB".equalsIgnoreCase(scope))
                    && StringUtils.isNotBlank(p.getScopeDeptIds())) {
                List<String> deptIds = JSON.parseArray(p.getScopeDeptIds(), String.class);
                if (deptIds != null) {
                    mergedDeptIds.addAll(deptIds);
                }
            }
        }

        merged.setDataScope(widestScope);
        // 仅 CUSTOM/DEPT/DEPT_WITH_SUB 需要 deptIds，ALL/SELF/EXPRESSION 不需要
        if (!mergedDeptIds.isEmpty() && ("CUSTOM".equals(widestScope) || "DEPT".equals(widestScope) || "DEPT_WITH_SUB".equals(widestScope))) {
            merged.setDeptIds(new ArrayList<>(mergedDeptIds));
        }

        return merged;
    }

    private Map<String, List<PermissionPolicy>> groupByKey(List<PermissionPolicy> policies) {
        if (CollectionUtils.isEmpty(policies)) {
            return Collections.emptyMap();
        }
        return policies.stream().collect(Collectors.groupingBy(
                p -> p.getAppCode() + ":" + p.getResourceCode()));
    }

    private List<String> parseActions(String actionsJson) {
        if (StringUtils.isBlank(actionsJson)) {
            return List.of();
        }
        List<String> list = JSON.parseArray(actionsJson, String.class);
        return list != null ? list : List.of();
    }

    private List<String> buildSourceDescriptions(List<PermissionPolicy> roleLevel,
                                                 List<PermissionPolicy> positionLevel) {
        List<String> sources = new ArrayList<>();
        if (!roleLevel.isEmpty()) {
            sources.add("角色策略(" + roleLevel.size() + "条)");
        }
        if (!positionLevel.isEmpty()) {
            sources.add("岗位策略(" + positionLevel.size() + "条)");
        }
        return sources;
    }
}
