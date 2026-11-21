package com.nway.system.authorization.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.LogicFieldStrategy;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源动作（受控词表）。
 * <p>
 * 未在此登记的动作不允许出现在策略中——避免“动作拼写错误导致策略永不生效”。
 */
@Data
@Table("sys_resource_action")
public class SysResourceAction {

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    /** 资源id */
    private String resourceId;

    /** 动作标识 */
    private String action;

    /** 展示名 */
    private String label;

    /** LOW/MEDIUM/HIGH */
    private String riskLevel;

    private Integer sort;

    private String creatorId;

    private String creatorName;

    private String modifierId;

    private String modifierName;

    private LocalDateTime createTime;

    private LocalDateTime modifiedTime;

    @Column(fillStrategy = LogicFieldStrategy.class, permissionStrategy = LogicFieldStrategy.class)
    private Boolean isDeleted;
}
