package com.nway.system.identity.web.model;

import lombok.Data;

@Data
public class UserRoleDto {

    private String userId;

    private String roleId;

    private String roleName;

    private String roleCode;
}
