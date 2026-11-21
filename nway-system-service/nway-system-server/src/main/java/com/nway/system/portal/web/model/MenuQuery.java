package com.nway.system.portal.web.model;

import lombok.Data;

@Data
public class MenuQuery {
    /** 菜单名 */
    private String name;
    /** 上级菜单id */
    private String parentId;
    /** 所属应用 */
    private String appCode;
}
