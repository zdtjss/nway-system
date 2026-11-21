package com.nway.system.authorization.domain;

import lombok.Data;

import java.util.List;

/**
 * 数据范围模型，用于应用数据范围与用户数据权限的交集计算。
 * <p>
 * 范围类型（从宽到窄）：ALL &gt; DEPT_WITH_SUB &gt; DEPT &gt; CUSTOM
 *
 * @author nway
 */
@Data
public class DataScope {

    private final String scopeType;
    private final List<String> deptIds;

    public DataScope(String scopeType, List<String> deptIds) {
        this.scopeType = scopeType;
        this.deptIds = deptIds != null ? deptIds : List.of();
    }

    public static DataScope all() {
        return new DataScope("ALL", List.of());
    }

    /**
     * 最小范围（不授予任何数据），用于上下文缺失等 fail-closed 场景。
     */
    public static DataScope none() {
        return new DataScope("NONE", List.of());
    }

    public boolean isAll() {
        return "ALL".equalsIgnoreCase(scopeType);
    }

    /**
     * 是否不授予任何数据。
     */
    public boolean isNone() {
        return "NONE".equalsIgnoreCase(scopeType);
    }

    /**
     * 计算两个数据范围的交集。
     * <p>
     * 规则：
     * <ul>
     *   <li>ALL ∩ X = X</li>
     *   <li>同类型：交集 deptIds</li>
     *   <li>不同类型：取较窄类型，deptIds 取交集</li>
     *   <li>交集为空 → CUSTOM 空列表（拒绝所有数据）</li>
     * </ul>
     *
     * @param a 数据范围 A
     * @param b 数据范围 B
     * @return 交集结果
     */
    public static DataScope intersect(DataScope a, DataScope b) {
        if (a == null || b == null) {
            return a != null ? a : (b != null ? b : all());
        }
        // NONE ∩ X = NONE（最小范围吸收）
        if (a.isNone()) {
            return a;
        }
        if (b.isNone()) {
            return b;
        }
        // ALL ∩ X = X
        if (a.isAll()) {
            return b;
        }
        if (b.isAll()) {
            return a;
        }
        // 同类型：交集 deptIds
        if (a.getScopeType().equalsIgnoreCase(b.getScopeType())) {
            List<String> intersected = intersectDeptIds(a.getDeptIds(), b.getDeptIds());
            return new DataScope(a.getScopeType(), intersected);
        }
        // 不同类型：取较窄类型
        int priorityA = scopePriority(a.getScopeType());
        int priorityB = scopePriority(b.getScopeType());
        // 取较窄（priority 值大的更窄）
        DataScope narrower = priorityA >= priorityB ? a : b;
        DataScope wider = priorityA >= priorityB ? b : a;
        // deptIds 取交集
        List<String> intersected = intersectDeptIds(a.getDeptIds(), b.getDeptIds());
        return new DataScope(narrower.getScopeType(), intersected);
    }

    private static List<String> intersectDeptIds(List<String> a, List<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) {
            return List.of();
        }
        return a.stream().filter(b::contains).distinct().toList();
    }

    /**
     * 范围优先级（值越大越窄）。
     */
    private static int scopePriority(String scopeType) {
        return switch (scopeType.toUpperCase()) {
            case "NONE" -> 4;
            case "DEPT_WITH_SUB" -> 1;
            case "DEPT" -> 2;
            case "CUSTOM" -> 3;
            default -> 0;
        };
    }
}
