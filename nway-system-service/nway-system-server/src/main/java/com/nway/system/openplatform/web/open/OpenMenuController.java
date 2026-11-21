package com.nway.system.openplatform.web.open;

import com.nway.system.common.web.Result;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.openplatform.web.context.OpenAppContext;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.portal.entity.Menu;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 开放平台：子应用菜单自注册 API。
 * <p>
 * 子应用部署新功能后，通过此 API 注册/更新自己的菜单，
 * 无需平台管理员手动配置。
 * <p>
 * 应用身份取自开放平台 Token（appCode），不信任任何请求头参数。
 */
@RestController
@RequestMapping("open/menu")
public class OpenMenuController {

    @Autowired
    private MenuDao menuDao;
    @Autowired
    private AppDao appDao;

    /**
     * 子应用注册菜单（全量替换 source=APP 的菜单）。
     * <p>
     * 注册的菜单来源标记为 APP，不影响平台管理员手动配置的 PLATFORM 菜单。
     * 只允许注册 Token 对应应用的菜单。
     *
     * @param menus 菜单列表
     */
    @PostMapping("register")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> register(@RequestBody List<Menu> menus) {
        String appCode = currentAppCode();
        App app = appDao.getByCode(appCode);
        if (app == null) {
            throw new NwaySystemException("应用不存在：" + appCode);
        }

        // 清除该应用下 source=APP 的旧菜单
        menuDao.deleteByAppAndSource(appCode, "APP");

        // 写入新菜单
        if (menus != null && !menus.isEmpty()) {
            for (Menu menu : menus) {
                menu.setAppCode(appCode);
                menu.setSource("APP");
                menu.setTenantCode(app.getTenantCode());
                if (menu.getIsEnabled() == null) {
                    menu.setIsEnabled(true);
                }
                if (StringUtils.isBlank(menu.getParentId())) {
                    menu.setParentId("");
                }
            }
            menuDao.batchInsert(menus);
        }

        return Result.ok();
    }

    /**
     * 查询应用已注册的菜单（子应用自检用）
     */
    @GetMapping("list")
    public Result<List<Menu>> list() {
        String appCode = currentAppCode();
        return Result.ok(menuDao.queryByApp(appCode));
    }

    /**
     * 从开放平台 Token 上下文获取当前应用编码。
     */
    private String currentAppCode() {
        OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
        if (appInfo == null || StringUtils.isBlank(appInfo.getAppCode())) {
            throw new NwaySystemException("无法识别应用身份");
        }
        return appInfo.getAppCode();
    }
}
