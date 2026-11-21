package com.nway.system.portal.web.model;

import lombok.Data;

@Data
public class FavMenuViewVo {

    private String id;
    /**
     * 菜单名
     */
    private String name;
    /**
     * 菜单地址
     */
    private String url;
    /**
     * 页面渲染类型
     */
    private String renderType;
    /**
     * 菜单类型；DIR:目录,PAGE:页面,BTN:按钮
     */
    private String type;
    /**
     * 图标
     */
    private String icon;
}
