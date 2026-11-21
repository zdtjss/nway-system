package com.nway.system.identity.web.model;

import lombok.Data;

import java.util.List;

/**
 * 角色-用户关系保存 DTO（按角色批量覆盖其下用户）
 */
@Data
public class RoleUserSaveDto {

    private String roleId;

    /** 该角色下应关联的用户工号列表 */
    private List<String> employeeIds;
}
