package com.nway.system.identity.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.LogicFieldStrategy;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import com.nway.system.common.persistence.TenantPermissionStrategy;
import com.nway.system.common.persistence.TenantReferenceCodeStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户岗位关联
 */
@Data
@Table("sys_user_position")
public class SysUserPosition {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    private String employeeId;
    private String positionId;
    private String deptId;
    private Boolean isPrimary;

    @Column(fillStrategy = TenantReferenceCodeStrategy.class, permissionStrategy = TenantPermissionStrategy.class)
    private String tenantCode;

    private String creatorId;
    private LocalDateTime createTime;

    @Column(fillStrategy = LogicFieldStrategy.class, permissionStrategy = LogicFieldStrategy.class)
    private Boolean isDeleted;
}
