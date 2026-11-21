package com.nway.system.portal.web.model;

import lombok.Data;

/**
 * 用户常用菜单
 */
@Data
public class MenuFavoriteVo {

    private String id;
    /**
     * 用户id
     */
    private String userId;
    /**
     * 菜单id
     */
    private String menuId;
    /**
     * 页面显示的位置
     */
    private String position;
    /**
     * APP:应用,DIR:目录,PAGE:页面
     */
    private String type;
}
