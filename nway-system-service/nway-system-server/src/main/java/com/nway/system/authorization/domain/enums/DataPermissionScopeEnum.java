package com.nway.system.authorization.domain.enums;

import com.nway.system.common.exception.NwaySystemException;

import java.util.Arrays;
import java.util.Optional;

/**
 * 数据权限范围类型
 */
public enum DataPermissionScopeEnum {

    /**
     * 全部数据
     */
    ALL("ALL", "全部数据", 100),
    /**
     * 仅本人创建
     */
    SELF("SELF", "仅本人", 0),
    /**
     * 本部门
     */
    DEPT("DEPT", "本部门", 3),
    /**
     * 本部门及下级
     */
    DEPT_WITH_SUB("DEPT_WITH_SUB", "本部门及下级", 4),
    /**
     * 自定义部门集合
     */
    CUSTOM("CUSTOM", "自定义", 2),
    /**
     * 条件表达式（灵活过滤）
     */
    EXPRESSION("EXPRESSION", "条件表达式", 1);

    private final String code;
    private final String name;
    private final int priority;

    DataPermissionScopeEnum(String code, String name, int priority) {
        this.code = code;
        this.name = name;
        this.priority = priority;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public static DataPermissionScopeEnum of(String code) {
        Optional<DataPermissionScopeEnum> first = Arrays.stream(values())
                .filter(e -> e.getCode().equals(code))
                .findFirst();
        if (first.isPresent()) {
            return first.get();
        }
        throw new NwaySystemException("无效的数据权限范围类型：" + code);
    }
}
