package com.nway.system.authorization.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.LogicFieldStrategy;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一授权（主体 × 资源 × 动作 × 数据范围）。
 * <p>
 * 与旧 {@code sys_permission_policy} 的差异：
 * <ul>
 *   <li>{@code actions} 必填，全部动作显式写 {@code ["*"]}（不再用 null 表示全部）；</li>
 *   <li>支持 {@code effect=DENY}（DENY 优先）；</li>
 *   <li>支持 {@code effective_from}/{@code expire_at} 时间窗与 {@code status}；</li>
 *   <li>行版本 {@code version}，配合全局授权版本做缓存失效。</li>
 * </ul>
 */
@Data
@Table("sys_grant")
public class SysGrant {

    public static final String EFFECT_ALLOW = "ALLOW";
    public static final String EFFECT_DENY = "DENY";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_SELF = "SELF";
    public static final String SCOPE_DEPT = "DEPT";
    public static final String SCOPE_DEPT_TREE = "DEPT_TREE";
    public static final String SCOPE_DEPT_LIST = "DEPT_LIST";
    public static final String SCOPE_NONE = "NONE";

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    /** ROLE/USER/POSITION/ORG/APP */
    private String subjectType;

    private String subjectId;

    private String appCode;

    /** 资源标识；{@code *} 表示该应用全部资源（需显式） */
    private String resourceCode;

    /** JSON 数组，非空；全部动作写 ["*"] */
    private String actions;

    /** ALLOW/DENY */
    private String effect;

    /** ALL/SELF/DEPT/DEPT_TREE/DEPT_LIST/NONE */
    private String dataScope;

    /** DEPT_LIST 的部门编码列表（JSON 数组） */
    private String scopeDeptIds;

    private LocalDateTime effectiveFrom;

    private LocalDateTime expireAt;

    private String status;

    private Long version;

    /** 主体归属租户 */
    private String tenantCode;

    private String creatorId;

    private String creatorName;

    private String modifierId;

    private String modifierName;

    private LocalDateTime createTime;

    private LocalDateTime modifiedTime;

    @Column(fillStrategy = LogicFieldStrategy.class, permissionStrategy = LogicFieldStrategy.class)
    private Boolean isDeleted;

    public boolean isAllow() {
        return !EFFECT_DENY.equals(effect);
    }

    /** 时间窗与状态是否有效 */
    public boolean isEffectiveAt(LocalDateTime now) {
        if (!STATUS_ACTIVE.equals(status)) {
            return false;
        }
        if (effectiveFrom != null && effectiveFrom.isAfter(now)) {
            return false;
        }
        return expireAt == null || expireAt.isAfter(now);
    }
}
