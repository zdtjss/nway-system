package com.nway.system.identity.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.identity.service.DeptService;
import com.nway.system.identity.service.UserDeptService;
import com.nway.system.identity.web.model.DeptDto;
import com.nway.system.common.web.Result;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.identity.web.model.DeptTreeVO;
import com.nway.system.identity.web.model.UserDeptVo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理：树查询、CRUD、人员部门关系维护
 */
@RequiresPermission(resource = "dept")
@RestController
@RequestMapping("dept")
public class DeptController {

    private final DeptService deptService;
    private final SysDeptDao sysDeptDao;
    private final UserDeptService userDeptService;

    public DeptController(DeptService deptService, SysDeptDao sysDeptDao, UserDeptService userDeptService) {
        this.deptService = deptService;
        this.sysDeptDao = sysDeptDao;
        this.userDeptService = userDeptService;
    }

    /**
     * 查询部门树（层级编码排序，含 children 嵌套结构）
     */
    @GetMapping("tree")
    public Result<List<DeptTreeVO>> tree() {
        return Result.ok(deptService.getTree());
    }

    /**
     * 按租户查询部门树（岗位等跨租户下拉用；非超管忽略租户参数，按当前租户隔离）
     */
    @GetMapping("treeByTenantCode")
    public Result<List<DeptTreeVO>> treeByTenantCode(@RequestParam(required = false) String tenantCode) {
        return Result.ok(deptService.getTreeByTenantCode(tenantCode));
    }

    /**
     * 查询当前用户权限范围内的部门树（根节点标识租户名称，用于调整部门等场景）
     */
    @GetMapping("permissionTree")
    public Result<List<DeptTreeVO>> permissionTree() {
        return Result.ok(deptService.getPermissionScopedTree());
    }

    /**
     * 查询全部有效部门（扁平列表，下拉选择用）
     */
    @GetMapping("queryAll")
    public Result<List<SysDept>> queryAll() {
        return Result.ok(sysDeptDao.queryAll());
    }

    /**
     * 新增/编辑部门（新增时自动生成层级编码）
     */
    @PostMapping("save")
    public Result<Void> save(@RequestBody DeptDto dto) {
        deptService.save(dto);
        return Result.ok();
    }

    /**
     * 删除部门（校验无子部门、无关联用户）
     */
    @PostMapping("deleteById")
    public Result<Void> deleteById(String id) {
        deptService.deleteById(id);
        return Result.ok();
    }

    /**
     * 查询部门下任职用户（含兼职，含用户信息与任职部门列表）
     */
    @GetMapping("users")
    public Result<List<UserDeptVo>> users(@RequestParam String deptId) {
        return Result.ok(userDeptService.queryUsersByDept(deptId));
    }

    /**
     * 分配用户到部门
     */
    @PostMapping("assignUserDept")
    public Result<Void> assignUserDept(@RequestParam String userId, @RequestParam String deptId) {
        deptService.assignUserDept(userId, deptId);
        return Result.ok();
    }
}
