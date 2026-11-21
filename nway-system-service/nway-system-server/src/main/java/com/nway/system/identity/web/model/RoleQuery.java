package com.nway.system.identity.web.model;


import com.nway.system.common.web.PageParam;
import lombok.Data;

@Data
public class RoleQuery extends PageParam {

    public String roleName;

    private String appCode;
}
