package com.nway.system.identity.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.identity.service.PositionService;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;
import com.nway.system.identity.entity.SysPosition;
import com.nway.system.identity.entity.SysUserPosition;
import com.nway.system.identity.web.model.PositionQuery;
import com.nway.system.identity.web.model.PositionUserVo;
import com.nway.system.identity.web.model.PositionVo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位管理
 */
@RequiresPermission(resource = "position")
@RestController
@RequestMapping("position")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * 分页查询岗位列表
     */
    @GetMapping("queryPage")
    public Result<PageVo<PositionVo>> queryPage(PositionQuery query) {
        return Result.ok(positionService.queryPage(query));
    }

    /**
     * 查询所有启用岗位
     */
    @GetMapping("queryAll")
    public Result<List<PositionVo>> queryAll() {
        return Result.ok(positionService.queryAll());
    }

    /**
     * 按状态筛选岗位
     */
    @GetMapping("queryByStatus")
    public Result<List<PositionVo>> queryByStatus(@RequestParam String status) {
        return Result.ok(positionService.queryByStatus(status));
    }

    /**
     * 根据ID查询岗位详情
     */
    @GetMapping("getById")
    public Result<SysPosition> getById(@RequestParam String id) {
        return Result.ok(positionService.getById(id));
    }

    /**
     * 新增/编辑岗位
     */
    @PostMapping("save")
    public Result<Void> save(@RequestBody SysPosition position) {
        positionService.save(position);
        return Result.ok();
    }

    /**
     * 删除岗位
     */
    @PostMapping("deleteById")
    public Result<Void> deleteById(@RequestParam String id) {
        positionService.deleteById(id);
        return Result.ok();
    }

    /**
     * 查询用户岗位列表
     */
    @GetMapping("userPositions")
    public Result<List<SysUserPosition>> userPositions(@RequestParam String employeeId) {
        return Result.ok(positionService.queryUserPositions(employeeId));
    }

    /**
     * 保存用户岗位关联（全量覆盖）
     */
    @PostMapping("saveUserPositions")
    public Result<Void> saveUserPositions(@RequestParam String employeeId,
                                          @RequestBody List<SysUserPosition> positions) {
        positionService.saveUserPositions(employeeId, positions);
        return Result.ok();
    }

    /**
     * 保存单条用户岗位关联（岗位列表“人员管理”入口，设置主岗时自动清除其他主岗）
     */
    @PostMapping("saveUserPosition")
    public Result<Void> saveUserPosition(@RequestBody SysUserPosition userPosition) {
        positionService.saveUserPosition(userPosition);
        return Result.ok();
    }

    /**
     * 移除单条用户岗位关联（主岗需先取消主岗标记）
     */
    @PostMapping("removeUserPosition")
    public Result<Void> removeUserPosition(@RequestParam String employeeId, @RequestParam String positionId) {
        positionService.removeUserPosition(employeeId, positionId);
        return Result.ok();
    }

    /**
     * 按岗位查人（含用户姓名与部门名称）
     */
    @GetMapping("usersByPosition")
    public Result<List<PositionUserVo>> usersByPosition(@RequestParam String positionId) {
        return Result.ok(positionService.queryUsersByPosition(positionId));
    }
}
