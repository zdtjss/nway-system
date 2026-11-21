package com.nway.system.identity.web.model;

import lombok.Data;

import java.util.List;

/**
 * 用户角度保存其角色
 */
@Data
public class UserRoleSaveDto {
    private String employeeId;
    private List<String> roleIds;
}
