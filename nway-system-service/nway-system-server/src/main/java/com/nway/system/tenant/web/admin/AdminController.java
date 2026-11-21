package com.nway.system.tenant.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.tenant.service.AdminGrantService;
import com.nway.system.tenant.web.model.AdminGrantDto;
import com.nway.system.common.web.Result;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员授权管理（仅超级管理员可操作）。
 * <p>
 * 管理员授权维度仅为租户：授权某租户即可管理该租户下的所有业务大类、角色、菜单、用户。
 * </p>
 */
@RequiresPermission(resource = "admin")
@RestController
@RequestMapping("admin")
public class AdminController {

    @Autowired
    private AdminGrantService adminGrantService;

    /**
     * 任命管理员并设置其可管理的租户列表。
     */
    @PostMapping("grant")
    public Result<Void> grant(@RequestBody AdminGrantDto dto) {
        requireSuperAdmin();
        adminGrantService.grant(dto.getEmployeeId(), dto.getTenantCodes());
        return Result.ok();
    }

    /**
     * 撤销管理员。
     */
    @PostMapping("revoke")
    public Result<Void> revoke(@RequestParam String employeeId) {
        requireSuperAdmin();
        adminGrantService.revoke(employeeId);
        return Result.ok();
    }

    /**
     * 查询指定用户的管理员授权。
     */
    @GetMapping("queryByEmployee")
    public Result<AdminGrantDto> queryByEmployee(@RequestParam String employeeId) {
        requireSuperAdmin();
        return Result.ok(adminGrantService.queryByEmployee(employeeId));
    }

    /**
     * 查询所有平台管理员及其授权范围。
     */
    @GetMapping("list")
    public Result<List<AdminGrantDto>> list() {
        requireSuperAdmin();
        return Result.ok(adminGrantService.listAdmins());
    }

    private void requireSuperAdmin() {
        if (!TenantUtil.isSuperAdmin()) {
            throw new NwaySystemException("仅超级管理员可管理管理员授权");
        }
    }
}
