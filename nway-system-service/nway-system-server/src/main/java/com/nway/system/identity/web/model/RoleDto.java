package com.nway.system.identity.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleDto {

    private String id;

    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    /** 所属应用标识 */
    private String appCode;

    /** 应用名称（展示用） */
    private String appName;

    /** 角色描述 */
    private String description;

    /** 租户编码 */
    private String tenantCode;

    /** 租户名称（展示用） */
    private String tenantName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifiedTime;
}
