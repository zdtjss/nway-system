package com.nway.system.authorization.web.admin;

import com.nway.system.authorization.service.decision.AuthzDecisionService;
import com.nway.system.authorization.service.decision.AuthzPreviewService;
import com.nway.system.authorization.service.grant.GrantCacheService;
import com.nway.system.authorization.service.grant.GrantCommandService;
import com.nway.system.authorization.service.grant.GrantTemplateService;
import com.nway.system.authorization.service.decision.SubjectContext;
import com.nway.system.authorization.service.decision.SubjectContextResolver;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.common.web.Result;
import com.nway.system.authorization.dao.GrantDao;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.entity.SysGrantChange;
import com.nway.system.authorization.entity.SysGrantTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 授权决策与授权维护 API（授权运营台的后端）。
 */
@RequiresPermission(resource = "role")
@RestController
@RequestMapping("authz")
public class AuthzController {

    private final AuthzDecisionService decisionService;
    private final GrantCacheService grantCacheService;
    private final GrantCommandService commandService;
    private final GrantDao grantDao;
    private final SubjectContextResolver subjectContextResolver;
    private final GrantTemplateService templateService;
    private final AuthzPreviewService previewService;

    public AuthzController(AuthzDecisionService decisionService,
                           GrantCacheService grantCacheService,
                           GrantCommandService commandService,
                           GrantDao grantDao,
                           SubjectContextResolver subjectContextResolver,
                           GrantTemplateService templateService,
                           AuthzPreviewService previewService) {
        this.decisionService = decisionService;
        this.grantCacheService = grantCacheService;
        this.commandService = commandService;
        this.grantDao = grantDao;
        this.subjectContextResolver = subjectContextResolver;
        this.templateService = templateService;
        this.previewService = previewService;
    }

    /**
     * 决策诊断：为什么某用户能/不能访问某资源动作。
     */
    @GetMapping("decision")
    public Result<AuthzDecisionService.Decision> decide(@RequestParam String employeeId,
                                                        @RequestParam String appCode,
                                                        @RequestParam String resourceCode,
                                                        @RequestParam String action) {
        SubjectContext subject = subjectContextResolver.fromEmployeeId(employeeId);
        return Result.ok(decisionService.decide(subject, appCode, resourceCode, action));
    }

    /**
     * 有效权限：ALLOW 减去 DENY 后的最终动作集合（按资源分组）。
     */
    @GetMapping("effective")
    public Result<List<EffectiveResource>> effective(@RequestParam String employeeId) {
        List<SysGrant> grants = grantCacheService.effectiveGrants(employeeId);
        Map<String, Set<String>> allow = new LinkedHashMap<>();
        Map<String, Set<String>> deny = new LinkedHashMap<>();
        for (SysGrant grant : grants) {
            Map<String, Set<String>> target = grant.isAllow() ? allow : deny;
            target.computeIfAbsent(grant.getAppCode() + ":" + grant.getResourceCode(),
                    k -> new LinkedHashSet<>()).addAll(actions(grant));
        }
        List<EffectiveResource> result = new ArrayList<>();
        allow.forEach((key, actions) -> {
            Set<String> denied = deny.getOrDefault(key, Set.of());
            Set<String> effective = new LinkedHashSet<>(actions);
            effective.removeAll(denied);
            if (!effective.isEmpty()) {
                String[] parts = key.split(":", 2);
                result.add(new EffectiveResource(parts[0], parts[1], List.copyOf(effective), List.of()));
            }
        });
        return Result.ok(result);
    }

    /** 查询主体的授权（含 DENY 与时间窗）。 */
    @GetMapping("grants")
    public Result<List<SysGrant>> grants(@RequestParam String subjectType,
                                         @RequestParam String subjectId) {
        return Result.ok(grantDao.queryBySubject(subjectType, subjectId));
    }

    /** 保存单条授权；reason 用于变更留痕。 */
    @PostMapping("grants/save")
    public Result<String> save(@RequestBody SysGrant grant,
                               @RequestParam(required = false) String reason) {
        return Result.ok(commandService.save(grant, reason));
    }

    /** 替换式保存角色授权。 */
    @PostMapping("grants/replaceRole")
    public Result<Void> replaceRole(@RequestParam String roleId,
                                    @RequestBody List<SysGrant> grants,
                                    @RequestParam(required = false) String reason) {
        commandService.replaceRoleGrants(roleId, grants, reason);
        return Result.ok();
    }

    /** 删除授权。 */
    @PostMapping("grants/delete")
    public Result<Void> delete(@RequestParam String id,
                               @RequestParam(required = false) String reason) {
        commandService.delete(id, reason);
        return Result.ok();
    }

    /** 变更留痕。 */
    @GetMapping("changes")
    public Result<List<SysGrantChange>> changes(@RequestParam String targetType,
                                                @RequestParam String targetId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(grantDao.queryChanges(targetType, targetId, Math.min(limit, 200)));
    }

    /** 回滚某次变更（按 before 快照重新应用）。 */
    @PostMapping("changes/rollback")
    public Result<Void> rollback(@RequestParam String changeId,
                                 @RequestParam(required = false) String reason) {
        commandService.rollback(changeId, reason);
        return Result.ok();
    }

    /** 影响面预演：不写库，返回受影响人数与动作增减。 */
    @PostMapping("preview")
    public Result<AuthzPreviewService.PreviewResult> preview(@RequestBody PreviewRequest request) {
        return Result.ok(previewService.preview(request.getSubjectType(),
                request.getSubjectId(), request.getGrants()));
    }

    /** 批量授权：多个主体应用同一资源动作/范围。 */
    @PostMapping("grants/batch")
    public Result<Integer> batch(@RequestBody BatchGrantRequest request) {
        return Result.ok(commandService.batch(request.getSubjectType(), request.getSubjectIds(),
                request.getGrant(), request.getReason()));
    }

    /** 模板列表。 */
    @GetMapping("templates")
    public Result<List<SysGrantTemplate>> templates() {
        return Result.ok(templateService.list());
    }

    /** 保存模板。 */
    @PostMapping("templates/save")
    public Result<String> saveTemplate(@RequestBody SysGrantTemplate template) {
        return Result.ok(templateService.save(template));
    }

    /** 应用模板到租户（开通套餐）。 */
    @PostMapping("templates/apply")
    public Result<GrantTemplateService.ApplyResult> applyTemplate(@RequestParam String templateId,
                                                                  @RequestParam String tenantCode,
                                                                  @RequestParam(required = false) String reason) {
        return Result.ok(templateService.apply(templateId, tenantCode, reason));
    }

    /** 删除模板。 */
    @PostMapping("templates/delete")
    public Result<Void> deleteTemplate(@RequestParam String id) {
        templateService.delete(id);
        return Result.ok();
    }

    /** 预演请求体。 */
    @lombok.Data
    public static class PreviewRequest {
        private String subjectType;
        private String subjectId;
        private List<SysGrant> grants;
    }

    /** 批量授权请求体。 */
    @lombok.Data
    public static class BatchGrantRequest {
        private String subjectType;
        private List<String> subjectIds;
        private SysGrant grant;
        private String reason;
    }

    private static List<String> actions(SysGrant grant) {
        if (grant.getActions() == null || grant.getActions().isBlank()) {
            return List.of();
        }
        try {
            List<String> list = com.alibaba.fastjson2.JSON.parseArray(grant.getActions(), String.class);
            return list == null ? List.of() : list;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public record EffectiveResource(String appCode, String resourceCode,
                                    List<String> actions,
                                    List<AuthzDecisionService.DataPredicate> dataPredicates) {
    }
}
