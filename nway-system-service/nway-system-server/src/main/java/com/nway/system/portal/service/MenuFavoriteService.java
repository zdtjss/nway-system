package com.nway.system.portal.service;

import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.FavoriteMenuDao;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.portal.entity.FavoriteMenu;
import com.nway.system.portal.entity.Menu;
import com.nway.system.portal.web.model.FavMenuViewVo;
import com.nway.system.portal.web.model.IndexPageMenuVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MenuFavoriteService {

    @Autowired
    private MenuDao menuDao;
    @Autowired
    private FavoriteMenuDao menuFavoriteDao;
    @Autowired
    private AppDao appDao;

    public void insertFav(String menuId) {

        String userId = LoginUserContext.current().getEmployeeId();

        FavoriteMenu menuFavorite = new FavoriteMenu();
        menuFavorite.setMenuId(menuId);
        menuFavorite.setUserId(userId);
        menuFavorite.setFavType("menu");

        if (!menuFavoriteDao.isInFav(userId, menuId)) {
            menuFavoriteDao.insert(menuFavorite);
        }
    }

    public void unstar(String menuId) {
        String userId = LoginUserContext.current().getEmployeeId();
        menuFavoriteDao.delete(userId, menuId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void favIndex(String menuId) {
        String userId = LoginUserContext.current().getEmployeeId();
        FavoriteMenu favoriteMenu = menuFavoriteDao.queryUserIndex(userId);
        if (favoriteMenu == null) {
            favoriteMenu = new FavoriteMenu();
            favoriteMenu.setUserId(userId);
            favoriteMenu.setMenuId(menuId);
            favoriteMenu.setFavType("index");
            menuFavoriteDao.insert(favoriteMenu);
        } else {
            menuFavoriteDao.updateIndexMenu(favoriteMenu.getId(), menuId);
        }
    }

    /**
     * 取消首页：删除该菜单的 index 记录。
     * <p>
     * 每个用户最多一条 index 记录，传入的菜单不是当前首页时删除 0 行，不报错（幂等）。
     */
    public void cancelIndex(String menuId) {
        String userId = LoginUserContext.current().getEmployeeId();
        menuFavoriteDao.deleteIndex(userId, menuId);
    }

    public IndexPageMenuVo listFavByUser(String userId) {

        IndexPageMenuVo indexPageMenuVo = new IndexPageMenuVo();

        List<FavoriteMenu> favoriteMenus = menuFavoriteDao.listByUser(userId);
        List<String> menuIds = favoriteMenus.stream().map(FavoriteMenu::getMenuId).toList();
        // 首页
        Optional<String> indexPageId = favoriteMenus.stream().filter(favoriteMenu -> "index".equals(favoriteMenu.getFavType())).map(FavoriteMenu::getMenuId).findFirst();
        // 常用
        List<String> favPageIds = favoriteMenus.stream().filter(favoriteMenu -> !"index".equals(favoriteMenu.getFavType())).map(FavoriteMenu::getMenuId).toList();

        List<Menu> menuList = menuDao.getByIds(menuIds);
        fillAppHost(menuList);
        List<FavMenuViewVo> menuViewVos = ConverterUtil.convert(menuList, FavMenuViewVo.class);

        if (indexPageId.isPresent()) {
            indexPageMenuVo.setIndex(menuViewVos.stream().filter(menuVo -> indexPageId.get().equals(menuVo.getId())).findFirst().orElse(null));
            indexPageMenuVo.setFavorite(menuViewVos.stream().filter(menuVo -> favPageIds.contains(menuVo.getId())).toList());
        } else {
            indexPageMenuVo.setFavorite(menuViewVos);
        }

        return indexPageMenuVo;
    }

    /**
     * 外部子应用菜单的 url 为相对路径，需拼接所在应用的 host 才能被前端识别为微应用。
     * <p>
     * 收藏与首页走的是本方法而非菜单树 {@code MenuService.buildTree}，此前未做拼接，
     * 导致收藏/设为首页的外部菜单（子应用菜单、低代码同步菜单）点击无反应。
     */
    private void fillAppHost(List<Menu> menus) {
        Set<String> appCodes = menus.stream()
                .map(Menu::getAppCode)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (appCodes.isEmpty()) {
            return;
        }
        Map<String, App> appMap = appDao.queryByCodes(appCodes);
        for (Menu menu : menus) {
            String url = menu.getUrl();
            if (StringUtils.isBlank(url) || url.startsWith("http")) {
                continue;
            }
            App app = appMap.get(menu.getAppCode());
            String host = (app != null && StringUtils.isNotBlank(app.getHost())) ? app.getHost() : "";
            if (StringUtils.isBlank(host)) {
                continue;
            }
            menu.setUrl(host.endsWith("/") || url.startsWith("/") ? host + url : host + "/" + url);
        }
    }
}
