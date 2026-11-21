package com.nway.system.portal.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

/**
 * 用户常用菜单
 */
@Data
@Table("sys_favorite_menu")
public class FavoriteMenu {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
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
     * 收藏类型 menu：菜单  index:首页
     */
    private String favType;
    /**
     * 顺序
     */
    private Integer seq;

}
