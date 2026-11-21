package com.nway.system.portal.web.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MenuDto {
    private String id;
    /** 菜单名 */
    @NotBlank(message = "菜单名称不能为空")
    private String name;
    /** 上级菜单id */
    private String parentId;
    private String parentName;
    /** 页面路由路径 */
    private String url;
    /** 页面渲染类型 */
    private String renderType;
    /** 图标 */
    private String icon;
    /** 所属应用标识 */
    private String appCode;
    /** 应用名称（展示用） */
    private String appName;
    /** 资源标识（PAGE类型） */
    private String resourceCode;
    /** 操作标识（BTN类型） */
    private String action;
    /** 菜单类型：DIR/PAGE/BTN */
    @NotBlank(message = "菜单类型不能为空")
    private String type;
    /** 排序 */
    private Integer sequence;
    /** 是否启用 */
    private Boolean isEnabled;
    /** 来源：PLATFORM/APP */
    private String source;
    /** 租户编码 */
    private String tenantCode;
    /** 租户名称（展示用） */
    private String tenantName;
    private Boolean hasChildren;
}
