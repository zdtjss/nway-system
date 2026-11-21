package com.nway.system.identity.web.model;

import com.nway.system.common.web.PageParam;
import lombok.Data;

@Data
public class UserRoleQuery extends PageParam {

    private String employeeId;

    private String roleId;
}
