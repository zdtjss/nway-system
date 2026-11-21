package com.nway.system.authorization.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.authorization.domain.MergedPolicy;
import com.nway.system.common.web.Result;
import com.nway.system.authorization.entity.PermissionPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限策略管理 Controller
 */
@RequiresPermission(resource = "role")
@RestController
@RequestMapping("policy")
public class PolicyController {

    @Autowired
    private PolicyService policyService;

    /**
     * 查询角色的全部策略
     */
    @GetMapping("queryByRole")
    public Result<List<PermissionPolicy>> queryByRole(@RequestParam String roleId) {
        return Result.ok(policyService.queryByRole(roleId));
    }

    /**
     * 查询主体的全部策略
     */
    @GetMapping("queryBySubject")
    public Result<List<PermissionPolicy>> queryBySubject(@RequestParam String subjectType,
                                                         @RequestParam String subjectId) {
        return Result.ok(policyService.queryBySubject(subjectType, subjectId));
    }

    /**
     * 查询开放平台应用（APP 主体）的策略。
     * <p>
     * 这些策略决定该应用换取开放 Token 时得到的 scope（见 OpenTokenService#resolveGrantedScopes）。
     */
    @GetMapping("queryByApp")
    public Result<List<PermissionPolicy>> queryByApp(@RequestParam String appCode) {
        return Result.ok(policyService.queryByAppSubject(appCode));
    }

    /**
     * 批量保存开放平台应用策略（替换式）
     */
    @PostMapping("saveAppPolicies")
    public Result<Void> saveAppPolicies(@RequestBody AppPoliciesRequest request) {
        policyService.saveAppPolicies(request.getAppCode(), request.getPolicies());
        return Result.ok();
    }

    /**
     * 保存单条策略
     */
    @PostMapping("save")
    public Result<Void> save(@RequestBody PermissionPolicy policy) {
        policyService.save(policy);
        return Result.ok();
    }

    /**
     * 批量保存角色策略（替换式）
     */
    @PostMapping("saveRolePolicies")
    public Result<Void> saveRolePolicies(@RequestBody RolePoliciesRequest request) {
        policyService.saveRolePolicies(request.getRoleId(), request.getPolicies());
        return Result.ok();
    }

    /**
     * 删除策略
     */
    @PostMapping("delete")
    public Result<Void> delete(@RequestParam String id) {
        policyService.delete(id);
        return Result.ok();
    }

    /**
     * 获取用户合并后的策略（权限总览 / 开放接口）
     */
    @GetMapping("merged")
    public Result<List<MergedPolicy>> getMergedPolicies(@RequestParam String employeeId,
                                                        @RequestParam(required = false) String appCode) {
        if (StringUtils.isNotBlank(appCode)) {
            return Result.ok(policyService.getMergedPoliciesByApp(employeeId, appCode));
        }
        Map<String, MergedPolicy> all = policyService.getMergedPolicies(employeeId);
        return Result.ok(all.values().stream().collect(Collectors.toList()));
    }

    /**
     * 批量保存请求体
     */
    @lombok.Data
    public static class RolePoliciesRequest {
        private String roleId;
        private List<PermissionPolicy> policies;
    }

    /**
     * 开放平台应用策略批量保存请求体
     */
    @lombok.Data
    public static class AppPoliciesRequest {
        private String appCode;
        private List<PermissionPolicy> policies;
    }
}
