package com.nway.system.authorization.web.admin;

import com.nway.system.authorization.service.resource.ResourceService;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.common.web.Result;
import com.nway.system.authorization.web.model.ResourceSaveRequest;
import com.nway.system.authorization.web.model.ResourceVo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资源登记台 API：资源与动作的唯一维护入口。
 * <p>
 * 权限归属 {@code nway-system:menu}（与菜单管理同一资源）：资源登记属于菜单/导航运营的一部分。
 */
@RequiresPermission(resource = "menu")
@RestController
@RequestMapping("resource")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** 资源列表（含动作） */
    @GetMapping("list")
    public Result<List<ResourceVo>> list(@RequestParam(required = false) String appCode,
                                         @RequestParam(required = false) String tenantScope) {
        return Result.ok(resourceService.list(appCode, tenantScope));
    }

    /** 资源详情 */
    @GetMapping("get")
    public Result<ResourceVo> get(@RequestParam String id) {
        return Result.ok(resourceService.getById(id));
    }

    /** 新增/更新资源并替换动作；返回资源id */
    @PostMapping("save")
    public Result<String> save(@RequestBody ResourceSaveRequest request) {
        return Result.ok(resourceService.save(request));
    }

    /** 资源下线（DEPRECATED，存量策略保留、新授权不可选） */
    @PostMapping("deprecate")
    public Result<Void> deprecate(@RequestParam String id) {
        resourceService.deprecate(id);
        return Result.ok();
    }

    /** 接口覆盖报表：声明来源、资源/动作登记情况 */
    @GetMapping("coverage")
    public Result<ResourceService.CoverageReport> coverage() {
        return Result.ok(resourceService.coverage());
    }
}
