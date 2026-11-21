package com.nway.system.openplatform.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放平台调用日志。
 * <p>
 * 不继承 BaseEntity（日志表无审计字段，create_time 由数据库 DEFAULT CURRENT_TIMESTAMP 设置）。
 *
 * @author nway
 */
@Data
@Table("sys_open_api_log")
public class SysOpenApiLog {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    private String appCode;
    private String apiPath;
    private String httpMethod;
    private String requestParams;
    private Integer responseStatus;
    private Integer durationMs;
    private String ip;
    private String employeeId;
    private String errorMsg;
    private String tenantCode;
    private LocalDateTime createTime;
}
