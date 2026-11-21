package com.nway.system.tenant.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

/**
 * 管理员可管理的授权租户列表。
 * <p>
 * 授权关系表，不继承 {@link com.nway.system.common.persistence.BaseEntity}，
 * 避免被租户隔离策略（TenantPermissionStrategy）影响。
 * </p>
 */
@Data
@Table("sys_admin_tenant")
public class AdminTenant {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    /**
     * 管理员工号
     */
    private String employeeId;

    /**
     * 可管理的租户编码
     */
    private String tenantCode;
}