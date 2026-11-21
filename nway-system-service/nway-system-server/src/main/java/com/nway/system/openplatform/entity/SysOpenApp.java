package com.nway.system.openplatform.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 开放平台应用。
 *
 * @author nway
 */
@Data
@Table("sys_open_app")
@EqualsAndHashCode(callSuper = false)
public class SysOpenApp extends BaseEntity {

    /**
     * 应用编码
     */
    private String appCode;
    /**
     * 应用名称
     */
    private String appName;
    /**
     * 密钥（BCrypt 加密存储）
     */
    private String appSecret;
    /**
     * 状态：using/disabled
     */
    private String status;
    /**
     * 已授权 scope JSON 数组，如 ["user:read","org:read"]
     */
    private String grantedScopes;
    /**
     * 应用数据范围类型：ALL/DEPT/DEPT_WITH_SUB/CUSTOM
     */
    private String dataScopeType;
    /**
     * 可访问部门 ID JSON 数组，CUSTOM 时有值
     */
    private String dataScopeDeptIds;
    /**
     * 每秒请求上限
     */
    private Integer rateLimit;
    /**
     * 每日请求上限
     */
    private Integer dailyLimit;
    /**
     * IP 白名单 JSON，空为不限
     */
    private String allowedIps;
    /**
     * 负责人工号
     */
    private String ownerEmployeeId;
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
}
