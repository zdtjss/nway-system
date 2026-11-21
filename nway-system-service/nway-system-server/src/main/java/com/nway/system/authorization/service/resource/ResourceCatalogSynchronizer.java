package com.nway.system.authorization.service.resource;

import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import com.nway.system.authorization.entity.SysResourceAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源目录同步（代码 → 库）。
 * <p>
 * 注解声明的资源/动作是事实源；启动时把缺失项补登记到 {@code sys_resource} /
 * {@code sys_resource_action}，让存量库无需手工补数据。
 * <p>
 * 生产可设 {@code nway.authz.catalog-sync=false}，此时缺失项交给
 * {@link ResourceManifestValidator} 按 {@code nway.authz.catalog-check} 阻断启动。
 */
@Component
@Order(0)
public class ResourceCatalogSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ResourceCatalogSynchronizer.class);

    /** 审计列 creator_id 为 varchar(10)，标记必须不超过 10 字符 */
    private static final String SYNC_ACTOR = "codesync";

    private final ResourceManifestProvider manifestProvider;
    private final ResourceDao resourceDao;

    @Value("${nway.authz.catalog-sync:true}")
    private boolean syncEnabled;

    public ResourceCatalogSynchronizer(ResourceManifestProvider manifestProvider, ResourceDao resourceDao) {
        this.manifestProvider = manifestProvider;
        this.resourceDao = resourceDao;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!syncEnabled) {
            log.info("资源目录自动同步已关闭（nway.authz.catalog-sync=false）");
            return;
        }
        ResourceManifest manifest = manifestProvider.rebuild();
        Map<String, SysResource> resources = new HashMap<>();
        for (SysResource resource : resourceDao.listAll()) {
            resources.put(resource.getAppCode() + ":" + resource.getResourceCode(), resource);
        }
        int newResources = 0;
        int newActions = 0;
        for (ResourceManifest.Entry entry : manifest.entries()) {
            if (entry.source() != ResourceManifest.Source.ANNOTATION || entry.resourceCode() == null) {
                continue;
            }
            String key = entry.appCode() + ":" + entry.resourceCode();
            SysResource resource = resources.get(key);
            if (resource == null) {
                resource = createResource(entry);
                resourceDao.insert(resource);
                resources.put(key, resource);
                newResources++;
            }
            Set<String> actions = new LinkedHashSet<>(entry.actions());
            for (String action : actions) {
                if (resourceDao.getAction(resource.getId(), action) == null) {
                    resourceDao.insertAction(createAction(resource.getId(), action));
                    newActions++;
                }
            }
        }
        if (newResources > 0 || newActions > 0) {
            manifestProvider.rebuild();
            log.info("资源目录已同步：新增资源 {} 个、动作 {} 个", newResources, newActions);
        }
    }

    private SysResource createResource(ResourceManifest.Entry entry) {
        SysResource resource = new SysResource();
        resource.setAppCode(entry.appCode());
        resource.setResourceCode(entry.resourceCode());
        resource.setResourceType(SysResource.TYPE_PAGE);
        resource.setName(entry.resourceCode());
        resource.setDescription("由代码注解自动登记");
        resource.setParentId("");
        boolean platform = "nway-system".equals(entry.appCode());
        resource.setTenantScope(platform ? SysResource.SCOPE_PLATFORM : SysResource.SCOPE_SHARED);
        resource.setOwnerTenantCode(platform ? "default" : "public");
        resource.setStatus(SysResource.STATUS_ACTIVE);
        resource.setSource("CODE");
        resource.setSort(0);
        resource.setCreatorId(SYNC_ACTOR);
        resource.setCreateTime(java.time.LocalDateTime.now());
        resource.setIsDeleted(Boolean.FALSE);
        return resource;
    }

    private SysResourceAction createAction(String resourceId, String action) {
        SysResourceAction row = new SysResourceAction();
        row.setResourceId(resourceId);
        row.setAction(action);
        row.setLabel(action);
        row.setRiskLevel(List.of("delete", "reset", "grant", "revoke", "export").contains(action)
                ? SysResourceAction.RISK_HIGH : SysResourceAction.RISK_LOW);
        row.setSort(0);
        row.setCreatorId(SYNC_ACTOR);
        row.setCreateTime(java.time.LocalDateTime.now());
        row.setIsDeleted(Boolean.FALSE);
        return row;
    }
}
