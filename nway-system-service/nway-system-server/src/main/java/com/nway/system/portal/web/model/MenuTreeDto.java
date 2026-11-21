package com.nway.system.portal.web.model;

import lombok.Data;

import java.util.List;

/**
 * 首页菜单
 */
@Data
public class MenuTreeDto extends MenuDto {

    private List<MenuTreeDto> children;

    /**
     * 只有菜单类型是页面时才赋值，标识配置到页面中的按钮权限标识
     */
    private List<String> powerKeys;
}
