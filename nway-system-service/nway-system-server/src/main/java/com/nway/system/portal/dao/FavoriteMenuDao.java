package com.nway.system.portal.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.system.portal.entity.FavoriteMenu;
import com.nway.system.portal.entity.Menu;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 常用菜单
 */
@Repository
public class FavoriteMenuDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(FavoriteMenu favorite) {
        sqlExecutor.insert(favorite);
    }

    public List<FavoriteMenu> listByUser(String userId) {
        return sqlExecutor.queryList(SQL.query(FavoriteMenu.class).eq(FavoriteMenu::getUserId, userId));
    }

    /**
     * 该菜单是否已在当前用户的「常用」列表中。
     * <p>
     * 必须限定 {@code fav_type='menu'}：同一菜单被「设为首页」后会存一条 {@code fav_type='index'} 记录，
     * 若只按 user_id + menu_id 判重，这条首页记录会让该菜单永远无法再被「设为常用」（插入被静默跳过）。
     * 与只删 menu 记录的 {@link #delete} 保持对称。
     */
    public boolean isInFav(String userId, String menuId) {
        return sqlExecutor.exist(SQL.query(FavoriteMenu.class).eq(FavoriteMenu::getUserId, userId).eq(FavoriteMenu::getMenuId, menuId).eq(FavoriteMenu::getFavType, "menu"));
    }

    /**
     * 删除常用的菜单，不包括首页
     *
     * @param userId
     * @param menuId
     */
    public void delete(String userId, String menuId) {
        sqlExecutor.delete(SQL.delete(FavoriteMenu.class).eq(FavoriteMenu::getUserId, userId).eq(FavoriteMenu::getMenuId, menuId).eq(FavoriteMenu::getFavType, "menu"));
    }

    /**
     * 删除指定菜单的「首页」记录（取消首页）。
     * <p>
     * 限定 {@code fav_type='index'}，不影响该菜单的常用记录；
     * 取消后该菜单不再是首页时，前端回落到默认欢迎页（见 menuStore.setFavorite）。
     */
    public void deleteIndex(String userId, String menuId) {
        sqlExecutor.delete(SQL.delete(FavoriteMenu.class).eq(FavoriteMenu::getUserId, userId).eq(FavoriteMenu::getMenuId, menuId).eq(FavoriteMenu::getFavType, "index"));
    }

    public FavoriteMenu queryUserIndex(String userId) {
        return sqlExecutor.queryOne(SQL.query(FavoriteMenu.class).eq(FavoriteMenu::getUserId, userId).eq(FavoriteMenu::getFavType, "index"));
    }

    public void updateIndexMenu(String favId, String menuId) {
        sqlExecutor.update(SQL.update(FavoriteMenu.class).set(FavoriteMenu::getFavType, "index").set(FavoriteMenu::getMenuId, menuId).eq(FavoriteMenu::getId, favId));
    }
}
