package com.nway.system.portal.web.admin;

import com.nway.system.portal.service.MenuFavoriteService;
import com.nway.system.portal.service.MenuService;
import com.nway.system.common.security.PublicApi;
import com.nway.system.portal.web.model.MenuFavoriteDto;
import com.nway.system.common.web.Result;
import com.nway.system.portal.web.model.UserMenuVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@PublicApi(reason = "当前用户菜单与常用功能，登录用户均可访问")
@RestController
@RequestMapping("myMenu")
public class UserMenuController {

    @Autowired
    private MenuService menuService;
    @Autowired
    private MenuFavoriteService menuFavoriteService;

    /**
     * 获取当前用户的全部可见菜单树（allMenu.vue 全屏菜单页调用）
     */
    @GetMapping
    public Result<UserMenuVo> listUserMenu() {
        return Result.ok(menuService.listUserMenu());
    }

    /**
     * 新增用户常用
     */
    @PostMapping("saveFav")
    public Result<Void> insert(@RequestBody MenuFavoriteDto menuFavoriteDTO) {
        menuFavoriteService.insertFav(menuFavoriteDTO.getMenuId());
        return Result.ok();
    }

    /**
     * 取消收藏
     */
    @PostMapping("unstar")
    public Result<Void> unstar(String menuId) {
        menuFavoriteService.unstar(menuId);
        return Result.ok();
    }

    /**
     * 设为首页
     */
    @PostMapping("favIndex")
    public Result<Void> favIndex(String menuId) {
        menuFavoriteService.favIndex(menuId);
        return Result.ok();
    }

    /**
     * 取消首页
     */
    @PostMapping("unstarIndex")
    public Result<Void> unstarIndex(String menuId) {
        menuFavoriteService.cancelIndex(menuId);
        return Result.ok();
    }

    /**
     * 获取用户常用菜单和菜单树（toolbar 登录后加载，含 menuList + favorite）
     */
    @GetMapping("userFav")
    public Result<UserMenuVo> userFav() {
        return Result.ok(menuService.listUserMenu());
    }
}
