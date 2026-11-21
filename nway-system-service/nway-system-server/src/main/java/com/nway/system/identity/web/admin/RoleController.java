package com.nway.system.identity.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;


import com.nway.system.identity.service.RoleService;
import com.nway.system.tenant.service.TenantService;
import com.nway.system.identity.web.model.RoleDto;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.web.PageVo;
import com.nway.system.portal.entity.Menu;
import com.nway.system.identity.web.model.RoleQuery;
import com.nway.system.common.web.Result;
import com.nway.system.authorization.web.model.ResourceVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiresPermission(resource = "role")
@RestController
@RequestMapping("role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private TenantService tenantService;

    /**
     * 保存角色
     *
     * @param roleDTO 菜单
     * @return Result
     */
    @PostMapping("save")
    public Result<Void> insert(@Valid @RequestBody RoleDto roleDTO) {
        roleService.save(roleDTO);
        return Result.ok();
    }

    /**
     * 删除角色
     *
     * @param id id
     * @return Result
     */
    @PostMapping("deleteById")
    public Result<Void> deleteById(String id) {
        roleService.deleteById(id);
        return Result.ok();
    }

    /**
     * 查询角色分页
     *
     * @param roleDTO 角色
     * @return Result
     */
    @GetMapping("queryPage")
    public Result<PageVo<RoleDto>> queryPage(RoleQuery roleDTO) {
        return Result.ok(roleService.queryPage(roleDTO));
    }

    /**
     * 根据ID查询
     *
     * @param id id
     * @return Result
     */
    @GetMapping("queryById")
    public Result<RoleDto> queryById(String id) {
        return Result.ok(roleService.queryById(id));
    }

    /**
     * 查询可分配给指定用户的角色列表（按用户租户过滤）。
     * <p>
     * 用于用户-角色绑定时的角色选择下拉。
     *
     * @param employeeId 目标用户工号
     * @return 该用户所属租户下的角色列表
     */
    @GetMapping("queryAvailableForUser")
    public Result<java.util.List<RoleDto>> queryAvailableForUser(@RequestParam String employeeId) {
        return Result.ok(roleService.queryAvailableForUser(employeeId));
    }

    /**
     * 角色归属租户 / 复制目标租户下拉：按当前用户可管理范围收敛。
     * <p>
     * 归属 role 权限而非 tenant 权限：租户管理员没有租户管理权限，
     * 直接调用 tenant/queryAll 会 403 并拖垮角色列表首屏加载。
     *
     * @return 超管为全部启用租户；管理员为授权租户；普通用户为本租户
     */
    @GetMapping("tenantOptions")
    public Result<List<TenantDto>> tenantOptions() {
        return Result.ok(tenantService.queryManageable());
    }

    /**
     * 策略配置弹窗的授权候选菜单：租户管理员通常没有菜单管理权限，
     * 不能复用 menu/queryByApp（403 会导致菜单树为空）。
     *
     * @param appCode    应用编码
     * @param tenantCode 目标角色租户（服务端校验调用方可读）
     */
    @GetMapping("grantableMenus")
    public Result<List<Menu>> grantableMenus(@RequestParam String appCode,
                                             @RequestParam(required = false) String tenantCode) {
        return Result.ok(roleService.queryGrantableMenus(appCode, tenantCode));
    }

    /**
     * 策略配置弹窗的资源动作目录：同上，复用 role 权限并沿用资源目录可见性规则。
     *
     * @param appCode 应用编码
     */
    @GetMapping("grantableResources")
    public Result<List<ResourceVo>> grantableResources(@RequestParam String appCode) {
        return Result.ok(roleService.queryGrantableResources(appCode));
    }

    /**
     * 复制角色到目标租户（新租户开通支撑：把模板角色连同其策略克隆到目标租户）。
     *
     * @param sourceRoleId     模板角色 id
     * @param targetTenantCode 目标租户编码
     * @return 新建的角色
     */
    @PostMapping("copyToTenant")
    public Result<RoleDto> copyToTenant(@RequestParam String sourceRoleId,
                                        @RequestParam String targetTenantCode) {
        return Result.ok(roleService.copyToTenant(sourceRoleId, targetTenantCode));
    }

}
