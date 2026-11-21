package com.nway.system.openplatform.web.admin;

import com.nway.system.openplatform.web.model.OpenAppVo;
import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.openplatform.service.OpenAppService;
import com.nway.system.common.web.Result;
import com.nway.system.openplatform.entity.SysOpenApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 开放平台应用管理 Controller（内部管理后台）。
 *
 * @author nway
 */
@RequiresPermission(resource = "open_app")
@RestController
@RequestMapping("openApp")
public class OpenAppController {

    @Autowired
    private OpenAppService openAppService;

    /**
     * 查询所有应用。
     */
    @GetMapping("queryAll")
    public Result<List<com.nway.system.openplatform.web.model.OpenAppVo>> queryAll() {
        return Result.ok(openAppService.queryAll());
    }

    /**
     * 按 ID 查询应用。
     */
    @GetMapping("getById")
    public Result<SysOpenApp> getById(String id) {
        return Result.ok(openAppService.getById(id));
    }

    /**
     * 创建应用，返回明文密钥（仅此一次）。
     */
    @PostMapping("create")
    public Result<Map<String, Object>> create(@RequestBody SysOpenApp app) {
        String plainSecret = openAppService.createApp(app);
        return Result.ok(Map.of("appId", app.getId(), "appSecret", plainSecret));
    }

    /**
     * 更新应用基本信息。
     */
    @PostMapping("update")
    public Result<Void> update(@RequestBody SysOpenApp app) {
        openAppService.updateApp(app);
        return Result.ok();
    }

    /**
     * 重置密钥，返回新明文密钥。
     */
    @PostMapping("resetSecret")
    public Result<Map<String, Object>> resetSecret(String appId) {
        String plainSecret = openAppService.resetSecret(appId);
        return Result.ok(Map.of("appSecret", plainSecret));
    }

    /**
     * 配置 scope 授权。
     */
    @PostMapping("grantScopes")
    public Result<Void> grantScopes(String appId, @RequestBody List<String> scopes) {
        openAppService.grantScopes(appId, scopes);
        return Result.ok();
    }

    /**
     * 配置数据范围。
     */
    @PostMapping("configureDataScope")
    public Result<Void> configureDataScope(String appId, String scopeType, @RequestBody(required = false) List<String> deptIds) {
        openAppService.configureDataScope(appId, scopeType, deptIds);
        return Result.ok();
    }

    /**
     * 启用/禁用应用。
     */
    @PostMapping("toggleStatus")
    public Result<Void> toggleStatus(String appId, @RequestParam boolean enable) {
        openAppService.toggleStatus(appId, enable);
        return Result.ok();
    }

    /**
     * 删除应用。
     */
    @PostMapping("delete")
    public Result<Void> delete(String appId) {
        openAppService.deleteApp(appId);
        return Result.ok();
    }
}
