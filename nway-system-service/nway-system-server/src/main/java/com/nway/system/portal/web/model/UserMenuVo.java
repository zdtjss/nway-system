package com.nway.system.portal.web.model;

import lombok.Data;

import java.util.List;

/**
 * 用户所有菜单
 */
@Data
public class UserMenuVo {

    /**
     * 首页（用户设置的收藏首页）
     */
    private FavMenuViewVo index;

    /**
     * 常用
     */
    private List<FavMenuViewVo> favorite;

    /**
     * 外部链接
     */
    private List<MenuVo> sys;

    /**
     * 所有菜单
     */
    private List<MenuTreeVo> menuList;
}
