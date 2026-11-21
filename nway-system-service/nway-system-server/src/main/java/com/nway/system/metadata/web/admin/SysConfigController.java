package com.nway.system.metadata.web.admin;

import com.nway.system.metadata.web.model.SysConfigVo;
import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.metadata.service.SysConfigService;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;
import com.nway.system.metadata.entity.SysConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置管理
 */
@RequiresPermission(resource = "sys_config")
@RestController
@RequestMapping("sysConfig")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @PostMapping("save")
    public Result<Void> save(@RequestBody SysConfig config) {
        sysConfigService.save(config);
        return Result.ok();
    }

    @PostMapping("delete")
    public Result<Void> delete(String id) {
        sysConfigService.delete(id);
        return Result.ok();
    }

    @GetMapping("queryPage")
    public Result<PageVo<com.nway.system.metadata.web.model.SysConfigVo>> queryPage(String configKey, String configName, String configGroup, String status,
                                               int page, int pageSize) {
        return Result.ok(sysConfigService.queryPage(configKey, configName, configGroup, status, page, pageSize));
    }

    @GetMapping("getByKey")
    public Result<SysConfig> getByKey(String configKey) {
        return Result.ok(sysConfigService.getByKey(configKey));
    }
}
