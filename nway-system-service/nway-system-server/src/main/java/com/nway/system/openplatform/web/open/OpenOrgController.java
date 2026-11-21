package com.nway.system.openplatform.web.open;

import com.nway.system.identity.service.DeptService;
import com.nway.system.identity.service.PositionService;
import com.nway.system.openplatform.service.OpenDataScopeService;
import com.nway.system.common.web.Result;
import com.nway.system.identity.web.model.PositionUserVo;
import com.nway.system.identity.web.model.PositionVo;
import com.nway.system.identity.web.model.DeptTreeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放平台组织架构接口。
 * <p>
 * 所有查询均受开放数据范围约束（应用数据范围 ∩ 代理用户数据权限）。
 *
 * @author nway
 */
@RestController
@RequestMapping("/open/org")
public class OpenOrgController {

    @Autowired
    private DeptService deptService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private OpenDataScopeService openDataScopeService;

    /**
     * 获取部门树（按开放数据范围过滤）。
     */
    @GetMapping("deptTree")
    public Result<List<DeptTreeVO>> deptTree() {
        OpenDataScopeService.OpenDataScope scope = openDataScopeService.resolve();
        return Result.ok(deptService.getTreeByDeptIds(scope.unrestricted() ? null : List.copyOf(scope.allowedDeptIds())));
    }

    /**
     * 查询所有启用岗位。
     */
    @GetMapping("positions")
    public Result<List<PositionVo>> positions() {
        return Result.ok(positionService.queryByStatus("using"));
    }

    /**
     * 按岗位查询用户列表（含用户姓名与部门名称，按开放数据范围过滤）。
     */
    @GetMapping("usersByPosition")
    public Result<List<PositionUserVo>> usersByPosition(@RequestParam String positionId) {
        List<PositionUserVo> users = positionService.queryUsersByPosition(positionId);
        OpenDataScopeService.OpenDataScope scope = openDataScopeService.resolve();
        if (!scope.unrestricted()) {
            // 范围受限时：仅保留主部门在范围内的人员（全局岗位任职记录不返回，fail-closed）
            users = users.stream()
                    .filter(u -> scope.allowsUser(u.getEmployeeId(), u.getDeptId()))
                    .toList();
        }
        return Result.ok(users);
    }
}
