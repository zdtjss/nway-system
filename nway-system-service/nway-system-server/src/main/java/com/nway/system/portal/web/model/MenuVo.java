package com.nway.system.portal.web.model;

import lombok.Data;

@Data
public class MenuVo {
    private String id;
    /** 菜单名 */
    private String name;
    /** 上级菜单id */
    private String parentId;
    /** 上级菜单名称 */
    private String parentName;
    /** 页面路由路径 */
    private String url;
    /** 页面渲染类型 */
    private String renderType;
    /** 图标 */
    private String icon;
    /** 所属应用标识 */
    private String appCode;
    /** 资源标识 */
    private String resourceCode;
    /** 操作标识 */
    private String action;
    /** 菜单类型：DIR/PAGE/BTN */
    private String type;
    /** 排序 */
    private Integer sequence;
    /** 是否启用 */
    private Boolean isEnabled;
    /** 来源 */
    private String source;
}
