package com.nway.system.authorization.service.resource;

import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import com.nway.system.authorization.entity.SysResourceAction;
import com.nway.system.authorization.web.model.ResourceSaveRequest;
import com.nway.system.authorization.web.model.ResourceVo;
import com.nway.system.authorization.web.interceptor.ApiResourceRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 资源目录服务：资源与动作的唯一维护入口。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>资源标识与动作受格式/词表校验——拼写错误在写入时就拒绝，而不是变成永不生效的策略；</li>
 *   <li>平台内置资源（PLATFORM）只有超级管理员可维护，共享/私有资源按租户管辖权校验；</li>
 *   <li>动作替换式保存，删动作前由调用方确认（策略可能仍引用，S2 起由决策内核显式处理）；</li>
 *   <li>资源变更后重建接口清单（{@link ResourceManifestProvider#rebuild()}）。</li>
 * </ul>
 */
@Service
public class ResourceService {

    /** 资源标识：字母开头，允许 字母/数字/下划线/中划线/冒号/点（如 user、wform:designer） */
    private static final Pattern RESOURCE_CODE_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_:.\\-]{0,49}$");

    /**
     * 受控动作词表（过渡期取平台与 wform 的并集，S2 收敛为唯一词表）。
     */
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "view", "read", "create", "edit", "write", "delete",
            "export", "import", "approve", "execute", "design", "publish",
            "reset", "grant", "revoke");

    /** 高危动作：管理端需要显式确认，策略页面应给出风险提示 */
    private static final Set<String> HIGH_RISK_ACTIONS = Set.of(
            "delete", "reset", "grant", "revoke", "export");

    private final ResourceDao resourceDao;
    private final ResourceManifestProvider manifestProvider;

    public ResourceService(ResourceDao resourceDao, ResourceManifestProvider manifestProvider) {
        this.resourceDao = resourceDao;
        this.manifestProvider = manifestProvider;
    }

    /**
     * 查询资源（含动作）。可见性：PLATFORM/SHARED 全部可见；TENANT 仅归属租户可见。
     */
    public List<ResourceVo> list(String appCode, String tenantScope) {
        List<SysResource> resources = StringUtils.isBlank(appCode)
                ? resourceDao.listAll()
                : resourceDao.listByApp(appCode);
        if (StringUtils.isNotBlank(tenantScope)) {
            resources = resources.stream()
                    .filter(r -> tenantScope.equalsIgnoreCase(r.getTenantScope()))
                    .toList();
        }
        List<SysResource> visible = resources.stream().filter(this::isVisible).toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<String, List<SysResourceAction>> actions = actionsByResource(
                visible.stream().map(SysResource::getId).toList());
        return visible.stream().map(r -> toVo(r, actions.getOrDefault(r.getId(), List.of()))).toList();
    }

    public List<ResourceVo> listByApp(String appCode) {
        return list(appCode, null);
    }

    public ResourceVo getById(String id) {
        SysResource resource = resourceDao.getById(id);
        if (resource == null || !isVisible(resource)) {
            throw new NwaySystemException("资源不存在或无权查看");
        }
        return toVo(resource, resourceDao.listActions(id));
    }

    /**
     * 保存资源（新增或更新）并替换动作。
     */
    @Transactional(rollbackFor = Exception.class)
    public String save(ResourceSaveRequest request) {
        if (request == null || StringUtils.isBlank(request.getResourceCode())) {
            throw new NwaySystemException("资源标识不能为空");
        }
        String code = request.getResourceCode().trim();
        if (!RESOURCE_CODE_PATTERN.matcher(code).matches()) {
            throw new NwaySystemException("资源标识格式非法：" + code
                    + "（须字母开头，仅字母/数字/下划线/中划线/冒号/点，长度 ≤50）");
        }
        String appCode = StringUtils.defaultIfBlank(request.getAppCode(),
                ApiResourceRegistry.PLATFORM_APP_CODE);

        SysResource existing = StringUtils.isBlank(request.getId())
                ? resourceDao.getByAppAndCode(appCode, code)
                : resourceDao.getById(request.getId());
        if (StringUtils.isNotBlank(request.getId()) && existing == null) {
            throw new NwaySystemException("资源不存在：" + request.getId());
        }

        SysResource resource = existing != null ? existing : new SysResource();
        if (existing != null) {
            // 身份字段不可变：改 app/resource_code 会让存量策略、菜单、开放 scope 全部脱节
            if (!existing.getAppCode().equals(appCode) || !existing.getResourceCode().equals(code)) {
                throw new NwaySystemException("资源所属应用与标识不可修改");
            }
        } else {
            resource.setAppCode(appCode);
            resource.setResourceCode(code);
        }

        String scope = StringUtils.defaultIfBlank(request.getTenantScope(), resource.getTenantScope());
        scope = StringUtils.defaultIfBlank(scope, SysResource.SCOPE_TENANT).toUpperCase(Locale.ROOT);
        if (!Set.of(SysResource.SCOPE_PLATFORM, SysResource.SCOPE_SHARED, SysResource.SCOPE_TENANT).contains(scope)) {
            throw new NwaySystemException("不支持的资源作用域：" + scope);
        }
        resource.setTenantScope(scope);
        resource.setOwnerTenantCode(resolveOwnerTenant(request, scope, resource));
        assertCanManage(resource);

        resource.setName(StringUtils.defaultIfBlank(request.getName(),
                StringUtils.defaultIfBlank(resource.getName(), code)));
        if (request.getDescription() != null) {
            resource.setDescription(request.getDescription());
        }
        String type = StringUtils.defaultIfBlank(request.getResourceType(), resource.getResourceType());
        resource.setResourceType(StringUtils.defaultIfBlank(type, SysResource.TYPE_PAGE).toUpperCase(Locale.ROOT));
        resource.setStatus(StringUtils.defaultIfBlank(request.getStatus(),
                StringUtils.defaultIfBlank(resource.getStatus(), SysResource.STATUS_ACTIVE)).toUpperCase(Locale.ROOT));
        resource.setSource(StringUtils.defaultIfBlank(resource.getSource(), "PLATFORM"));

        if (existing == null) {
            resourceDao.insert(resource);
        } else {
            resourceDao.updateById(resource);
        }

        if (request.getActions() != null) {
            replaceActions(resource.getId(), request.getActions());
        }
        manifestProvider.rebuild();
        return resource.getId();
    }

    /**
     * 资源下线：标记 DEPRECATED。存量策略保留，但新授权不可再选择该资源。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deprecate(String id) {
        SysResource resource = resourceDao.getById(id);
        if (resource == null) {
            throw new NwaySystemException("资源不存在");
        }
        assertCanManage(resource);
        resource.setStatus(SysResource.STATUS_DEPRECATED);
        resourceDao.updateById(resource);
        manifestProvider.rebuild();
    }

    /**
     * 覆盖报表：接口清单 vs 资源目录（缺资源/缺动作/未声明）。
     */
    public CoverageReport coverage() {
        ResourceManifest manifest = manifestProvider.get();
        Set<String> resources = new LinkedHashSet<>();
        for (ResourceManifest.Entry entry : manifest.entries()) {
            if (entry.resourceCode() != null) {
                resources.add(entry.appCode() + ":" + entry.resourceCode());
            }
        }

        Map<String, SysResource> dbResources = new LinkedHashMap<>();
        for (String key : resources) {
            String[] parts = key.split(":", 2);
            SysResource resource = resourceDao.getByAppAndCode(parts[0], parts[1]);
            if (resource != null) {
                dbResources.put(key, resource);
            }
        }
        Map<String, Set<String>> dbActions = actionsByResource(
                dbResources.values().stream().map(SysResource::getId).toList())
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().map(SysResourceAction::getAction)
                                .collect(Collectors.toCollection(LinkedHashSet::new))));

        List<CoverageItem> items = new ArrayList<>();
        for (ResourceManifest.Entry entry : manifest.entries()) {
            String key = entry.resourceCode() == null ? null : entry.appCode() + ":" + entry.resourceCode();
            SysResource resource = key == null ? null : dbResources.get(key);
            List<String> missingActions = List.of();
            if (resource != null && !entry.actions().isEmpty()) {
                Set<String> registered = dbActions.getOrDefault(resource.getId(), Set.of());
                missingActions = entry.actions().stream().filter(a -> !registered.contains(a)).toList();
            }
            items.add(new CoverageItem(entry.pattern(), entry.appCode(), entry.resourceCode(),
                    entry.actions(), entry.source().name(),
                    resource != null, resource == null ? List.of() : missingActions));
        }
        return new CoverageReport(
                manifest.entries().size(),
                (int) manifest.count(ResourceManifest.Source.ANNOTATION),
                (int) manifest.count(ResourceManifest.Source.LEGACY),
                (int) manifest.count(ResourceManifest.Source.UNCOVERED),
                (int) manifest.count(ResourceManifest.Source.PUBLIC),
                manifest.annotationCoverage(),
                dbResources.size(),
                (int) resources.stream().filter(k -> !dbResources.containsKey(k)).count(),
                items);
    }

    // ====== 内部 ======

    private void replaceActions(String resourceId, List<ResourceSaveRequest.ActionItem> actions) {
        Set<String> seen = new LinkedHashSet<>();
        List<SysResourceAction> rows = new ArrayList<>();
        int sort = 0;
        for (ResourceSaveRequest.ActionItem item : actions) {
            if (item == null || StringUtils.isBlank(item.getAction())) {
                throw new NwaySystemException("动作不能为空");
            }
            String action = item.getAction().trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_ACTIONS.contains(action)) {
                throw new NwaySystemException("不支持的动作：" + action + "，允许值 " + ALLOWED_ACTIONS);
            }
            if (!seen.add(action)) {
                continue;
            }
            SysResourceAction row = new SysResourceAction();
            row.setResourceId(resourceId);
            row.setAction(action);
            row.setLabel(StringUtils.defaultIfBlank(item.getLabel(), action));
            row.setRiskLevel(HIGH_RISK_ACTIONS.contains(action)
                    ? SysResourceAction.RISK_HIGH : SysResourceAction.RISK_LOW);
            row.setSort(sort++);
            rows.add(row);
        }
        resourceDao.deleteActions(resourceId);
        resourceDao.batchInsertActions(rows);
    }

    private Map<String, List<SysResourceAction>> actionsByResource(Collection<String> resourceIds) {
        if (CollectionUtils.isEmpty(resourceIds)) {
            return Map.of();
        }
        return resourceDao.listActions(resourceIds).stream()
                .collect(Collectors.groupingBy(SysResourceAction::getResourceId));
    }

    private String resolveOwnerTenant(ResourceSaveRequest request, String scope, SysResource existing) {
        if (SysResource.SCOPE_PLATFORM.equals(scope)) {
            return TenantUtil.DEFAULT_TENANT_CODE;
        }
        String requested = StringUtils.firstNonBlank(request.getOwnerTenantCode(), existing.getOwnerTenantCode());
        if (StringUtils.isNotBlank(requested)) {
            return requested;
        }
        String current = TenantUtil.currentTenantCode();
        if (StringUtils.isBlank(current)) {
            throw new NwaySystemException("无法确定资源归属租户，请显式指定 ownerTenantCode");
        }
        return current;
    }

    private void assertCanManage(SysResource resource) {
        if (TenantUtil.isSuperAdmin()) {
            return;
        }
        if (SysResource.SCOPE_PLATFORM.equals(resource.getTenantScope())) {
            throw new NwaySystemException("平台内置资源仅超级管理员可维护");
        }
        TenantUtil.assertAdminCanManageTenant(resource.getOwnerTenantCode());
    }

    private boolean isVisible(SysResource resource) {
        if (resource.isShareable()) {
            return true;
        }
        List<String> manageable = TenantUtil.currentManageableTenantCodes();
        return manageable == null || manageable.contains(resource.getOwnerTenantCode());
    }

    private static ResourceVo toVo(SysResource resource, List<SysResourceAction> actions) {
        return new ResourceVo(resource.getId(), resource.getAppCode(), resource.getResourceCode(),
                resource.getResourceType(), resource.getName(), resource.getDescription(),
                resource.getTenantScope(), resource.getOwnerTenantCode(), resource.getStatus(),
                actions.stream().map(a -> new ResourceVo.ActionVo(
                        a.getAction(), a.getLabel(), a.getRiskLevel())).toList());
    }

    /**
     * 覆盖报表。
     */
    public record CoverageReport(int totalEndpoints, int annotated, int legacy, int uncovered, int publicApi,
                                 double annotationCoverage, int registeredResources, int missingResources,
                                 List<CoverageItem> items) {
    }

    public record CoverageItem(String pattern, String appCode, String resourceCode,
                               List<String> actions, String source, boolean registered,
                               List<String> missingActions) {
    }
}
