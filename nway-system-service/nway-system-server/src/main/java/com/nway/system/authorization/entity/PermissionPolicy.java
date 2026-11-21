package com.nway.system.authorization.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 统一权限策略视图（对外兼容 {@code /policy/*} 管理端）。
 * <p>
 * 物理存储已在 S2 切换为 {@code sys_grant}：
 * <ul>
 *   <li>{@code actions} 必填，全部动作显式写 {@code ["*"]}（不再用 null 表示全部）；</li>
 *   <li>{@code dataScope} 为平台语义：ALL/SELF/DEPT/DEPT_TREE/DEPT_LIST/NONE；</li>
 *   <li>{@code priority} 与 {@code scopeCondition} 已删除（无可执行语义）。</li>
 * </ul>
 */
@Data
@Table("sys_permission_policy")
public class PermissionPolicy extends BaseEntity {

    /** 主体类型：ROLE/USER/POSITION/APP（APP 的 subjectId 为开放平台应用编码） */
    private String subjectType;

    /** 主体id（角色id/工号/岗位id/开放平台应用编码） */
    private String subjectId;

    /** 应用标识 */
    private String appCode;

    /** 资源标识（*=该应用全部资源） */
    private String resourceCode;

    /** 允许的操作，JSON 数组；必填，全部动作显式写 ["*"] */
    private String actions;

    /** 数据范围：ALL/SELF/DEPT/DEPT_WITH_SUB/CUSTOM/EXPRESSION */
    private String dataScope;

    /** CUSTOM时的部门id列表（JSON数组） */
    private String scopeDeptIds;
}
