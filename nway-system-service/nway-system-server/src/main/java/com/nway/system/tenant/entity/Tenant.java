package com.nway.system.tenant.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.LogicFieldStrategy;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 租户信息。
 * <p>
 * 不继承 BaseEntity —— 租户是顶层隔离边界，不应被自身的租户隔离策略约束。
 * 仅保留逻辑删除策略（SELECT/UPDATE/DELETE 自动过滤已删除记录）。
 * </p>
 */
@Data
@Table("sys_tenant")
public class Tenant {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    /** 租户编码 */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 状态：using/disabled */
    private String status;

    /** 到期日期，为空表示长期有效 */
    private LocalDate expireDate;

    /** 备注 */
    private String remark;

    /** 创建人id */
    private String creatorId;

    /** 创建人名称 */
    private String creatorName;

    /** 修改人Id */
    private String modifierId;

    /** 修改人名称 */
    private String modifierName;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改日期 */
    private LocalDateTime modifiedTime;

    /** 删除标记 */
    @Column(fillStrategy = LogicFieldStrategy.class, permissionStrategy = LogicFieldStrategy.class)
    private Boolean isDeleted;
}
