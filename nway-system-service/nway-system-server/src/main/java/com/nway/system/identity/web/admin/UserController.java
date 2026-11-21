package com.nway.system.identity.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.identity.service.UserService;
import com.nway.system.identity.web.model.UserDto;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;
import com.nway.system.identity.web.model.UserQuery;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RequiresPermission(resource = "user")
@RestController
@RequestMapping("user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("getById")
    public Result<UserDto> getById(String id) {
        return Result.ok(userService.getById(id));
    }

    /**
     * 保存用户（新增/编辑）。
     * <p>
     * 新增时返回一次性初始密码（仅本次响应可见，用户首次登录须改密）；
     * 编辑时返回 null。
     */
    @PostMapping("save")
    public Result<String> add(@Valid @RequestBody UserDto userDto) {
        return Result.ok(userService.save(userDto));
    }

    @GetMapping("queryPage")
    public Result<PageVo<UserDto>> queryPage(UserQuery query) {
        return Result.ok(userService.queryPage(query));
    }

    /**
     * 查询可分配给指定角色的用户列表（按角色租户过滤）。
     * <p>
     * 用于角色-用户绑定时的用户候选列表。
     *
     * @param roleId  角色 ID
     * @param keyword 搜索关键字（工号/姓名）
     * @return 该角色所属租户下的用户分页列表
     */
    @GetMapping("queryForRoleBinding")
    public Result<PageVo<UserDto>> queryForRoleBinding(@RequestParam String roleId,
                                                       @RequestParam(required = false) String keyword) {
        return Result.ok(userService.queryForRoleBinding(roleId, keyword));
    }

    /**
     * 禁用用户（不可物理删除，仅禁用）
     *
     * @param employeeId 工号
     */
    @PostMapping("disable")
    public Result<Void> disable(String employeeId) {
        userService.disableUser(employeeId);
        return Result.ok();
    }

    /**
     * 启用用户
     *
     * @param employeeId 工号
     */
    @PostMapping("enable")
    public Result<Void> enable(String employeeId) {
        userService.enableUser(employeeId);
        return Result.ok();
    }

    /**
     * 管理员重置指定用户密码为一次性随机密码（重置后该用户须重新登录并立即改密）
     *
     * @param employeeId 目标用户工号
     * @return 一次性初始密码（仅本次响应可见）
     */
    @PostMapping("resetPassword")
    public Result<String> resetPassword(String employeeId) {
        return Result.ok(userService.adminResetPassword(employeeId));
    }
}
