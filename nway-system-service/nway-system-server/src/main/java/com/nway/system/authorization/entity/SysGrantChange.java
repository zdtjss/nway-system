package com.nway.system.authorization.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授权变更留痕（与授权写入同事务，支持审计与回滚）。
 */
@Data
@Table("sys_grant_change")
public class SysGrantChange {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    /** 操作人工号 */
    private String actorId;

    /** 操作人租户 */
    private String actorTenant;

    /** 变更原因（管理员填写，审计必填建议） */
    private String reason;

    /** ROLE/USER/POSITION/APP/RESOURCE */
    private String targetType;

    private String targetId;

    /** 变更前 JSON */
    private String beforeJson;

    /** 变更后 JSON */
    private String afterJson;

    /** 变更后的全局授权版本 */
    private Long authzVersion;

    /** 请求/追踪 id（与日志关联） */
    private String requestId;

    private LocalDateTime createTime;
}
