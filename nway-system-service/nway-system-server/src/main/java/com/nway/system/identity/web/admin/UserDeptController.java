package com.nway.system.identity.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.identity.service.UserDeptService;
import com.nway.system.common.web.Result;
import com.nway.system.identity.entity.SysUserDept;
import com.nway.system.identity.web.model.UserDeptVo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户部门关系管理（跨部门任职）
 */
@RequiresPermission(resource = "user")
@RestController
@RequestMapping("userDept")
public class UserDeptController {

    private final UserDeptService userDeptService;

    public UserDeptController(UserDeptService userDeptService) {
        this.userDeptService = userDeptService;
    }

    /**
     * 查询用户所有任职部门
     */
    @GetMapping("queryByEmployee")
    public Result<List<SysUserDept>> queryByEmployee(@RequestParam String employeeId) {
        return Result.ok(userDeptService.queryByEmployee(employeeId));
    }

    /**
     * 查询部门下所有用户（含兼职）
     */
    @GetMapping("queryByDept")
    public Result<List<SysUserDept>> queryByDept(@RequestParam String deptId) {
        return Result.ok(userDeptService.queryByDept(deptId));
    }

    /**
     * 查询部门下所有用户（含兼职，含用户信息与任职部门列表）
     */
    @GetMapping("queryUsersByDept")
    public Result<List<UserDeptVo>> queryUsersByDept(@RequestParam String deptId) {
        return Result.ok(userDeptService.queryUsersByDept(deptId));
    }

    /**
     * 分配用户到部门：无任职部门时作为主部门，已有任职部门时追加为兼职
     */
    @PostMapping("assign")
    public Result<Void> assign(@RequestParam String employeeId, @RequestParam String deptId) {
        userDeptService.assignUserDept(employeeId, deptId);
        return Result.ok();
    }

    /**
     * 保存用户部门关系（全量覆盖：主部门 + 兼职部门列表）
     */
    @PostMapping("save")
    public Result<Void> save(@RequestParam String employeeId,
                             @RequestParam String primaryDeptId,
                             @RequestBody(required = false) List<String> secondaryDeptIds) {
        userDeptService.saveUserDepts(employeeId, primaryDeptId, secondaryDeptIds);
        return Result.ok();
    }

    /**
     * 变更主部门（部门调动）
     */
    @PostMapping("changePrimary")
    public Result<Void> changePrimary(@RequestParam String employeeId,
                                       @RequestParam String newPrimaryDeptId) {
        userDeptService.changePrimaryDept(employeeId, newPrimaryDeptId);
        return Result.ok();
    }

    /**
     * 添加兼职部门
     */
    @PostMapping("addSecondary")
    public Result<Void> addSecondary(@RequestParam String employeeId,
                                      @RequestParam String deptId) {
        userDeptService.addSecondaryDept(employeeId, deptId);
        return Result.ok();
    }

    /**
     * 移除兼职部门
     */
    @PostMapping("removeSecondary")
    public Result<Void> removeSecondary(@RequestParam String employeeId,
                                         @RequestParam String deptId) {
        userDeptService.removeSecondaryDept(employeeId, deptId);
        return Result.ok();
    }
}
