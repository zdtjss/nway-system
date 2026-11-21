package com.nway.system.audit.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志。
 * <p>
 * 不继承 BaseEntity（日志表无审计字段与逻辑删除，create_time 由数据库 DEFAULT CURRENT_TIMESTAMP 设置）。
 *
 * @author nway
 */
@Data
@Table("sys_audit_log")
public class AuditLog {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    private String operatorId;
    private String operatorName;
    private String operationType;
    private String apiPath;
    private String httpMethod;
    private String requestParams;
    private Integer responseStatus;
    private Integer durationMs;
    private String ip;
    private String userAgent;
    private String tenantCode;
    private LocalDateTime createTime;
}
