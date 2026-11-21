package com.nway.system.identity.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;


import com.nway.system.identity.service.UserRoleService;
import com.nway.system.identity.web.model.RoleUserDto;
import com.nway.system.identity.web.model.RoleUserSaveDto;
import com.nway.system.identity.web.model.UserRoleDto;
import com.nway.system.identity.web.model.UserRoleSaveDto;
import com.nway.system.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RequiresPermission(resource = "role")
@RestController
@RequestMapping("userRole")
public class UserRoleController {

    @Autowired
    private UserRoleService userRoleService;

    /**
     * 批量保存用户角色关系
     *
     * @param userRoleSaveDto 用户角色关系列表
     * @return Result
     */
    @PostMapping("save")
    public Result<Void> saveUserRole(@RequestBody UserRoleSaveDto userRoleSaveDto) {
        userRoleService.save(userRoleSaveDto);
        return Result.ok();
    }

    /**
     * 按角色批量保存其下用户（角色列表“用户管理”入口）
     *
     * @param dto 角色id + 用户工号列表
     * @return Result
     */
    @PostMapping("saveByRole")
    public Result<Void> saveUserRoleByRole(@RequestBody RoleUserSaveDto dto) {
        userRoleService.saveByRole(dto);
        return Result.ok();
    }

    @GetMapping("queryByUser")
    public Result<List<UserRoleDto>> queryByUser(String userId) {
        return Result.ok(userRoleService.queryByUser(userId));
    }

    @GetMapping("queryByRole")
    public Result<List<RoleUserDto>> queryByRole(String roleId) {
        return Result.ok(userRoleService.queryByRole(roleId));
    }

}
