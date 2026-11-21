package com.nway.system.identity.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.LogicFieldStrategy;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import com.nway.system.common.persistence.TenantReferenceCodeStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联
 */
@Data
@Table("sys_user_role")
public class UserRole {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    private String employeeId;

    private String roleId;

    /** 租户编码（冗余，取自角色所属租户；角色属公共租户时为引用语义） */
    @Column(fillStrategy = TenantReferenceCodeStrategy.class)
    private String tenantCode;

    private String creatorId;

    private LocalDateTime createTime;

    @Column(fillStrategy = LogicFieldStrategy.class, permissionStrategy = LogicFieldStrategy.class)
    private Boolean isDeleted;
}
