package com.nway.system.ai.web.admin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.system.tenant.service.TenantService;
import com.nway.system.ai.service.AiInterfacePermissionService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.web.model.AiInterfaceVo;
import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.web.Result;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.web.model.AppVo;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 接口元数据管理：CRUD、发布/下线、批量导入导出。
 *
 * @author nway
 */
@RequiresPermission(resource = "ai_interface")
@RestController
@RequestMapping("ai/interfaces")
public class AiInterfaceController {

    private final AiInterfaceService interfaceService;

    private final AiInterfacePermissionService permissionService;

    private final AppDao appDao;

    private final TenantService tenantService;

    private final TenantNameResolver tenantNameResolver;

    public AiInterfaceController(AiInterfaceService interfaceService,
                                 AiInterfacePermissionService permissionService,
                                 AppDao appDao,
                                 TenantService tenantService,
                                 TenantNameResolver tenantNameResolver) {
        this.interfaceService = interfaceService;
        this.permissionService = permissionService;
        this.appDao = appDao;
        this.tenantService = tenantService;
        this.tenantNameResolver = tenantNameResolver;
    }

    /**
     * 应用下拉选项：复用系统「应用」登记表（sys_app）。
     * <p>
     * 这里只读取应用的后端地址用于接口调用，不要求该应用配置菜单。
     * 数据层已按当前用户可读租户过滤，公共租户（public）下的应用即「公共应用」。
     */
    @GetMapping("apps")
    public Result<List<AppVo>> listApps() {
        List<AppVo> apps = ConverterUtil.convert(appDao.queryByStatus("using"), AppVo.class);
        Map<String, String> tenantNames = tenantNameResolver.resolveNames(
                apps.stream().map(AppVo::getTenantCode).toList());
        apps.forEach(app -> app.setTenantName(tenantNames.getOrDefault(app.getTenantCode(), "")));
        return Result.ok(apps);
    }

    /**
     * 归属租户下拉：仅返回当前用户可管理的租户。
     * <p>
     * 超管为全部启用租户；管理员为授权租户；普通用户为本租户。
     * 公共租户 public 仅在其被显式授权（或超管）时出现，与保存时的写守卫一致。
     */
    @GetMapping("tenants")
    public Result<List<TenantDto>> listTenants() {
        return Result.ok(tenantService.queryManageable());
    }

    @GetMapping
    public Result<List<AiInterfaceVo>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String appCode,
                                            @RequestParam(required = false) String tenantCode) {
        return Result.ok(interfaceService.list(status, keyword, appCode, tenantCode));
    }

    @GetMapping("{id}")
    public Result<AiInterfaceVo> get(@PathVariable String id) {
        return Result.ok(interfaceService.getById(id));
    }

    @PostMapping
    public Result<AiInterfaceVo> save(@RequestBody AiInterfaceConfig config) {
        return Result.ok(interfaceService.save(config));
    }

    @PostMapping("{id}/publish")
    public Result<Void> publish(@PathVariable String id) {
        interfaceService.publish(id);
        return Result.ok();
    }

    @PostMapping("{id}/offline")
    public Result<Void> offline(@PathVariable String id) {
        interfaceService.offline(id);
        return Result.ok();
    }

    /**
     * 接口权限详情：访问模式、资源码与已授权主体（含 ai:* 分组授权）。
     */
    @GetMapping("{id}/permissions")
    public Result<AiInterfacePermissionService.PermissionDetail> permissions(@PathVariable String id) {
        return Result.ok(permissionService.detail(id));
    }

    /**
     * 保存接口权限：{@code grants} 作用于当前接口，{@code allGrants} 作用于全部 AI 接口；
     * 为 null 表示不改动该作用域。
     */
    @PutMapping("{id}/permissions")
    public Result<AiInterfacePermissionService.PermissionDetail> savePermissions(
            @PathVariable String id, @RequestBody(required = false) PermissionsRequest request) {
        permissionService.save(id,
                request == null ? null : request.accessMode(),
                request == null ? null : request.grants(),
                request == null ? null : request.allGrants());
        interfaceService.invalidateCache();
        return Result.ok(permissionService.detail(id));
    }

    /**
     * 权限诊断：某工号为什么能/不能调用该接口。
     */
    @GetMapping("{id}/permissions/check")
    public Result<AiInterfacePermissionService.PermissionCheck> checkPermission(
            @PathVariable String id, @RequestParam String employeeId) {
        return Result.ok(permissionService.check(employeeId, id));
    }

    /**
     * 保存前影响面预览：授权主体覆盖的去重工号数。
     */
    @PostMapping("{id}/permissions/preview")
    public Result<AiInterfacePermissionService.PermissionPreview> previewPermissions(
            @PathVariable String id, @RequestBody(required = false) PermissionsRequest request) {
        return Result.ok(permissionService.preview(
                request == null ? null : request.grants(),
                request == null ? null : request.allGrants()));
    }

    /**
     * 调用测试：body 形如 {@code {"args": {...}}}，参数按 inputSchema 校验后发往目标应用。
     */
    @SuppressWarnings("unchecked")
    @PostMapping("{id}/test")
    public Result<AiInvokeResult> test(@PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Object args = body == null ? null : body.get("args");
        Map<String, Object> values = args instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        return Result.ok(interfaceService.testInvoke(id, values));
    }

    @DeleteMapping("{id}")
    public Result<Void> delete(@PathVariable String id) {
        interfaceService.delete(id);
        return Result.ok();
    }

    /**
     * 导入：支持单个对象、数组，或 {@code {"version":"2.0","interfaces":[...]}}。
     */
    @PostMapping(value = "import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Map<String, Object>> importConfigs(@RequestBody String body) {
        List<AiInterfaceConfig> configs;
        try {
            String trimmed = body == null ? "" : body.trim();
            if (trimmed.startsWith("[")) {
                configs = JSON.parseArray(trimmed, AiInterfaceConfig.class);
            } else {
                JSONObject root = JSON.parseObject(trimmed);
                JSONArray array = root.getJSONArray("interfaces");
                configs = array == null ? List.of() : array.toJavaList(AiInterfaceConfig.class);
            }
        } catch (Exception e) {
            throw new NwaySystemException("导入 JSON 解析失败：" + e.getMessage());
        }
        if (configs.isEmpty()) {
            throw new NwaySystemException("导入内容为空");
        }
        List<String> errors = new ArrayList<>();
        int saved = 0;
        for (AiInterfaceConfig config : configs) {
            try {
                // 导入只生成草稿，发布必须由人工审核后单独操作
                config.setStatus("DRAFT");
                interfaceService.save(config);
                saved++;
            } catch (Exception e) {
                errors.add(config.getInterfaceCode() + "：" + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", configs.size());
        result.put("saved", saved);
        result.put("errors", errors);
        return Result.ok(result);
    }

    @GetMapping("export")
    public Result<Map<String, Object>> exportConfigs() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", "2.0");
        result.put("interfaces", interfaceService.exportAll());
        return Result.ok(result);
    }

    /** 权限保存请求体。 */
    public record PermissionsRequest(String accessMode,
                                     List<AiInterfacePermissionService.GrantPayload> grants,
                                     List<AiInterfacePermissionService.GrantPayload> allGrants) {
    }
}
