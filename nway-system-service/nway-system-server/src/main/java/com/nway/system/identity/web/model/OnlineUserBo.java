package com.nway.system.identity.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 在线用户 BO。
 * <p>
 * 用于在线用户管理功能展示，其中 employeeId / sessionId / ip / userAgent / loginTime / expireTime
 * 来自 Redis 会话记录，realName / tenantName 在查询时由 UserService 补充。
 *
 * @author nway
 */
@Data
public class OnlineUserBo {

    /** 工号 */
    private String employeeId;
    /** 会话标识（对应 JWT 中的 sid） */
    private String sessionId;
    /** 真实姓名 */
    private String realName;
    /** 租户名称 */
    private String tenantName;
    /** 登录 IP */
    private String ip;
    /** User-Agent */
    private String userAgent;
    /** 登录时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime loginTime;
    /** Token 过期时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;
}
