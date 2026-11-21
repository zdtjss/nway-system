package com.nway.system.tenant.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.tenant.service.TenantService;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;
import com.nway.system.tenant.web.model.TenantQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户管理（仅超级管理员可操作）
 */
@RequiresPermission(resource = "tenant")
@RestController
@RequestMapping("tenant")
public class TenantController {

    @Autowired
    private TenantService tenantService;

    @PostMapping("save")
    public Result<Void> save(@RequestBody TenantDto tenantDto) {
        tenantService.save(tenantDto);
        return Result.ok();
    }

    @PostMapping("deleteById")
    public Result<Void> deleteById(String id) {
        tenantService.deleteById(id);
        return Result.ok();
    }

    @GetMapping("queryPage")
    public Result<PageVo<TenantDto>> queryPage(TenantQuery query) {
        return Result.ok(tenantService.queryPage(query));
    }

    @GetMapping("getById")
    public Result<TenantDto> getById(String id) {
        return Result.ok(tenantService.getById(id));
    }

    /**
     * 查询所有启用状态的租户（下拉选择用）
     */
    @GetMapping("queryAll")
    public Result<List<TenantDto>> queryAll() {
        return Result.ok(tenantService.queryAll());
    }

    /**
     * 查询当前管理员可管理的租户列表（工作租户切换用）。
     * <p>
     * 超管返回全部租户；管理员返回授权租户；普通用户返回空列表。
     */
    @GetMapping("myAdminTenants")
    public Result<List<TenantDto>> myAdminTenants() {
        return Result.ok(tenantService.queryMyAdminTenants());
    }
}
