package com.nway.system.portal.web.model;

import lombok.Data;

import java.util.List;

/**
 * 菜单树节点 VO
 */
@Data
public class MenuTreeVo {

    private String id;

    /** 菜单名 */
    private String name;

    /** 菜单地址（已拼接 app.host） */
    private String url;

    /** 图标 */
    private String icon;

    /** 菜单类型：MODULE/DIR/PAGE/BTN */
    private String type;

    /** 页面渲染类型 */
    private String renderType;

    /** 资源标识（PAGE 类型） */
    private String resourceCode;

    /** 操作标识（BTN 类型） */
    private String action;

    /** 菜单归属租户（低代码菜单记页面租户，手工菜单记应用租户）：作为打开子应用时的工作租户下发 */
    private String tenantCode;

    /** 子菜单 */
    private List<MenuTreeVo> children;
}
