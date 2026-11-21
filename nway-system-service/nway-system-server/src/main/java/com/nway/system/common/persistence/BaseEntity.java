package com.nway.system.common.persistence;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.LogicFieldStrategy;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import com.nway.system.common.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseEntity {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    /**
     * 租户编码，INSERT 时自动填充当前用户租户编码，SELECT/UPDATE/DELETE 时自动追加租户隔离条件
     */
    @Column(fillStrategy = TenantCodeStrategy.class, permissionStrategy = TenantPermissionStrategy.class)
    private String tenantCode;

    @Column(fillStrategy = CurrentEmployeeIdOnInsert.class)
    private String creatorId;

    @Column(fillStrategy = CurrentUserRealNameOnInsert.class)
    private String creatorName;

    @Column(fillStrategy = CurrentEmployeeId.class)
    private String modifierId;

    @Column(fillStrategy = CurrentUserRealName.class)
    private String modifierName;

    @Column(fillStrategy = LocalDateTimeNowOnInsert.class)
    private LocalDateTime createTime;

    @Column(fillStrategy = LocalDateTimeNow.class)
    private LocalDateTime modifiedTime;

    @Column(fillStrategy = LogicFieldStrategy.class, permissionStrategy = LogicFieldStrategy.class)
    private Boolean isDeleted;
}
