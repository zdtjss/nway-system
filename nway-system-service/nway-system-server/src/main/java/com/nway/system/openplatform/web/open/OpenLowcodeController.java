package com.nway.system.openplatform.web.open;

import com.nway.system.portal.service.LowcodeMenuService;
import com.nway.system.portal.service.LowcodeMenuSyncLock;
import com.nway.system.portal.web.model.LowcodeAppVo;
import com.nway.system.portal.web.model.LowcodeMenuNodeVo;
import com.nway.system.portal.web.model.LowcodeMenuOperateRequest;
import com.nway.system.portal.web.model.LowcodeMenuSyncRequest;
import com.nway.system.portal.web.model.LowcodeMenuSyncResponse;
import com.nway.system.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放平台：低代码平台菜单同步 API。
 * <p>
 * 低代码平台将配置的页面/目录同步注册为菜单（source=LOWCODE）。
 * 仅支持 user_delegate 授权模式，用户/租户身份取自 Token 代理用户；
 * 目标应用须为代理用户租户的应用或 public（公共租户）共享应用，
 * 菜单 tenant_code 记代理用户租户（作用域租户）。
 * <p>
 * scope：GET → menu:read，POST → menu:write（由 OpenApiInterceptor 映射）。
 */
@RestController
@RequestMapping("/open/lowcode")
public class OpenLowcodeController {

    @Autowired
    private LowcodeMenuService lowcodeMenuService;

    @Autowired
    private LowcodeMenuSyncLock menuSyncLock;

    /**
     * 查询当前租户下可发布的应用列表（供低代码平台选择"所属应用"）
     */
    @GetMapping("apps")
    public Result<List<LowcodeAppVo>> apps() {
        return Result.ok(lowcodeMenuService.listPublishableApps().stream()
                .map(LowcodeAppVo::from).toList());
    }

    /**
     * 查询应用下菜单树（供低代码平台选择"父级菜单"，仅 DIR/PAGE；根节点为应用本身）
     */
    @GetMapping("menu/tree")
    public Result<List<LowcodeMenuNodeVo>> menuTree(@RequestParam String appCode) {
        return Result.ok(lowcodeMenuService.queryMenuTree(appCode).stream()
                .map(LowcodeMenuNodeVo::from).toList());
    }

    /**
     * 菜单同步（幂等 upsert，重复发布=更新；整批校验通过后落库，任一失败整体回滚）。
     * <p>
     * 在事务外按目标应用持有跨实例分布式锁（{@link LowcodeMenuSyncLock}），
     * 多实例部署下同应用的发布串行执行；数据库活跃唯一索引作为最终兜底。
     */
    @PostMapping("menu/sync")
    public Result<LowcodeMenuSyncResponse> sync(@RequestBody LowcodeMenuSyncRequest request) {
        try (LowcodeMenuSyncLock.Handle ignored = menuSyncLock.lock(request.getAppCode())) {
            return Result.ok(lowcodeMenuService.sync(request));
        }
    }

    /**
     * 删除菜单（仅 LOWCODE 来源；存在子菜单时整批拒绝；未命中忽略，幂等）
     */
    @PostMapping("menu/remove")
    public Result<Void> remove(@RequestBody LowcodeMenuOperateRequest request) {
        try (LowcodeMenuSyncLock.Handle ignored = menuSyncLock.lock(request.getAppCode())) {
            lowcodeMenuService.remove(request);
            return Result.ok();
        }
    }

    /**
     * 停用菜单（保留配置，页面下线场景；幂等）
     */
    @PostMapping("menu/disable")
    public Result<Void> disable(@RequestBody LowcodeMenuOperateRequest request) {
        try (LowcodeMenuSyncLock.Handle ignored = menuSyncLock.lock(request.getAppCode())) {
            lowcodeMenuService.disable(request);
            return Result.ok();
        }
    }
}
