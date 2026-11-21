package com.nway.system.authorization.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.LogicFieldStrategy;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授权模板（租户开通套餐）：一组角色 + 角色内授权清单。
 */
@Data
@Table("sys_grant_template")
public class SysGrantTemplate {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    private String templateCode;

    private String name;

    private String description;

    /** 主应用（角色默认所属应用） */
    private String appCode;

    /** 角色与授权清单（JSON，结构见迁移脚本注释） */
    private String itemsJson;

    /** ACTIVE/DISABLED */
    private String status;

    private String creatorId;

    private String creatorName;

    private String modifierId;

    private String modifierName;

    private LocalDateTime createTime;

    private LocalDateTime modifiedTime;

    @Column(fillStrategy = LogicFieldStrategy.class, permissionStrategy = LogicFieldStrategy.class)
    private Boolean isDeleted;
}
