package com.nway.system.identity.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleVo {

    private String roleId;

    private String roleName;

    private String roleCode;

    private String businessName;

    private String creatorId;

    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime createTime;

    private String modifierId;

    private String modifierName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime modifiedTime;

}
