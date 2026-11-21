package com.nway.system.portal.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.portal.service.MenuService;
import com.nway.system.common.web.Result;
import com.nway.system.portal.entity.Menu;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理 Controller
 */
@RequiresPermission(resource = "menu")
@RestController
@RequestMapping("menu")
public class MenuController {

    @Autowired
    private MenuService menuService;

    /**
     * 管理后台菜单列表
     */
    @GetMapping("queryForAdmin")
    public Result<List<Menu>> queryForAdmin(@RequestParam(required = false) String appCode,
                                            @RequestParam(required = false) String parentId,
                                            @RequestParam(required = false) String name) {
        return Result.ok(menuService.queryForAdmin(appCode, parentId, name));
    }

    /**
     * 按应用查询菜单
     *
     * @param tenantCode 目标角色租户（策略配置弹窗传入）：仅返回该租户 + public 共享租户的菜单
     */
    @GetMapping("queryByApp")
    public Result<List<Menu>> queryByApp(@RequestParam String appCode,
                                         @RequestParam(required = false) String tenantCode) {
        return Result.ok(menuService.queryByApp(appCode, tenantCode));
    }

    /**
     * 获取单条菜单
     */
    @GetMapping("getById")
    public Result<Menu> getById(@RequestParam String id) {
        return Result.ok(menuService.getById(id));
    }

    /**
     * 保存菜单
     */
    @PostMapping("save")
    public Result<Void> save(@RequestBody Menu menu) {
        menuService.save(menu);
        return Result.ok();
    }

    /**
     * 删除菜单
     */
    @PostMapping("delete")
    public Result<Void> delete(@RequestParam String id) {
        menuService.delete(id);
        return Result.ok();
    }
}
