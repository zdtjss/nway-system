package com.nway.system.authorization.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 合并后的权限策略（用户最终权限的单资源视图）。
 * <p>
 * 由 PolicyMerger 从多条 PermissionPolicy 合并而来，是缓存和拦截器使用的最终结构。
 */
@Data
public class MergedPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 全动作通配符：actions 含此值表示该资源全部操作放行（与 null 等价，但语义显式） */
    public static final String ALL_ACTIONS = "*";

    /** 应用标识 */
    private String appCode;

    /** 资源标识 */
    private String resourceCode;

    /** 合并后允许的操作集合。null 或含 {@link #ALL_ACTIONS} 均表示全部操作（不限制） */
    private Set<String> actions;

    /** 合并后的数据范围 */
    private String dataScope;

    /** DEPT/CUSTOM 时的部门id列表 */
    private List<String> deptIds;

    /** DEPT_WITH_SUB 时的部门编码列表（用于 LIKE 前缀匹配） */
    private List<String> deptCodes;

    /** 策略来源描述（用于权限总览展示） */
    private List<String> sourceDescriptions;

    /**
     * 判断是否允许指定操作。
     * <p>
     * {@code actions} 为 null（历史语义）或含 {@link #ALL_ACTIONS}（显式语义）时视为全部放行。
     */
    public boolean hasAction(String action) {
        if (actions == null || actions.contains(ALL_ACTIONS)) {
            return true;
        }
        return actions.contains(action);
    }

    /**
     * 判断是否有任意操作权限（用于菜单可见性判断）
     */
    public boolean hasAnyAction() {
        return actions == null || !actions.isEmpty();
    }
}
