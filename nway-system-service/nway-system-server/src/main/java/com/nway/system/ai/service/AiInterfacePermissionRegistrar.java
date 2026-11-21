package com.nway.system.ai.service;

import com.nway.system.ai.entity.AiInterface;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import com.nway.system.authorization.entity.SysResourceAction;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 发布/保存接口时登记权限资源：{@code ai:{interfaceCode}} + {@code execute} 动作。
 * <p>
 * 资源只作为授权目录与租户作用域的载体，不再决定接口是否受控；
 * 是否受控由 {@code ai_interface.access_mode} 显式表达。
 * 同时维护 {@code ai:*} 分组资源，支持“全部 AI 接口”一次授权。
 *
 * @author nway
 */
@Service
public class AiInterfacePermissionRegistrar {

    public static final String APP_CODE = AiPermissionService.APP_CODE;

    public static final String GROUP_RESOURCE_CODE = "ai:*";

    private static final String GROUP_RESOURCE_ID = "ai_permission_group";

    private static final String ACTION = AiPermissionService.ACTION;

    private static final String CREATOR = "ai-sync";

    private final ResourceDao resourceDao;

    public AiInterfacePermissionRegistrar(ResourceDao resourceDao) {
        this.resourceDao = resourceDao;
    }

    /**
     * 幂等同步接口资源：存在则更新名称/租户作用域/状态，不存在则插入。
     */
    public void synchronize(AiInterface entity) {
        if (entity == null || StringUtils.isBlank(entity.getInterfaceCode())) {
            return;
        }
        ensureGroupResource();
        String resourceCode = AiResourceCodes.of(entity.getInterfaceCode());
        String tenantScope = tenantScope(entity.getTenantCode());
        String ownerTenant = ownerTenant(entity.getTenantCode());
        SysResource resource = resourceDao.getByAppAndCode(APP_CODE, resourceCode);
        if (resource == null) {
            resource = new SysResource();
            resource.setAppCode(APP_CODE);
            resource.setResourceCode(resourceCode);
            resource.setResourceType(SysResource.TYPE_CAPABILITY);
            resource.setParentId("");
            resource.setSource("APP");
            resource.setSort(0);
            resource.setCreatorId(CREATOR);
            resource.setCreatorName("AI 接口");
            resource.setCreateTime(LocalDateTime.now());
            resource.setIsDeleted(Boolean.FALSE);
        }
        resource.setName(StringUtils.defaultIfBlank(entity.getName(), entity.getInterfaceCode()));
        resource.setDescription("AI 接口：" + StringUtils.defaultString(entity.getDescription()));
        resource.setTenantScope(tenantScope);
        resource.setOwnerTenantCode(ownerTenant);
        resource.setStatus(SysResource.STATUS_ACTIVE);
        resource.setModifierId(CREATOR);
        resource.setModifierName("AI 接口");
        resource.setModifiedTime(LocalDateTime.now());
        if (resource.getId() == null) {
            resourceDao.insert(resource);
        } else {
            resourceDao.updateById(resource);
        }
        ensureAction(resource.getId());
    }

    /** 接口删除/改名后下线旧资源，保留历史授权审计。 */
    public void deprecate(String interfaceCode) {
        if (StringUtils.isBlank(interfaceCode)) {
            return;
        }
        SysResource resource = resourceDao.getByAppAndCode(APP_CODE, AiResourceCodes.of(interfaceCode));
        if (resource == null) {
            return;
        }
        resource.setStatus(SysResource.STATUS_DEPRECATED);
        resource.setModifierId(CREATOR);
        resource.setModifierName("AI 接口");
        resource.setModifiedTime(LocalDateTime.now());
        resourceDao.updateById(resource);
    }

    /** 确保“全部 AI 接口”分组资源存在（启动对账与每次同步都会调用，幂等）。 */
    public void ensureGroupResource() {
        SysResource group = resourceDao.getByAppAndCode(APP_CODE, GROUP_RESOURCE_CODE);
        if (group == null) {
            group = new SysResource();
            group.setId(GROUP_RESOURCE_ID);
            group.setAppCode(APP_CODE);
            group.setResourceCode(GROUP_RESOURCE_CODE);
            group.setResourceType(SysResource.TYPE_CAPABILITY);
            group.setName("全部 AI 接口");
            group.setDescription("AI 接口授权分组：授予后可调用全部 AI 接口");
            group.setParentId("");
            group.setTenantScope(SysResource.SCOPE_PLATFORM);
            group.setOwnerTenantCode("default");
            group.setStatus(SysResource.STATUS_ACTIVE);
            group.setSource("APP");
            group.setSort(0);
            group.setCreatorId(CREATOR);
            group.setCreatorName("AI 接口");
            group.setCreateTime(LocalDateTime.now());
            group.setIsDeleted(Boolean.FALSE);
            resourceDao.insert(group);
        }
        ensureAction(group.getId());
    }

    private void ensureAction(String resourceId) {
        if (resourceDao.getAction(resourceId, ACTION) != null) {
            return;
        }
        SysResourceAction action = new SysResourceAction();
        action.setResourceId(resourceId);
        action.setAction(ACTION);
        action.setLabel("调用");
        action.setRiskLevel(SysResourceAction.RISK_MEDIUM);
        action.setSort(0);
        action.setCreatorId(CREATOR);
        action.setCreatorName("AI 接口");
        action.setCreateTime(LocalDateTime.now());
        action.setIsDeleted(Boolean.FALSE);
        resourceDao.insertAction(action);
    }

    private static String tenantScope(String tenantCode) {
        if (TenantCodes.PUBLIC.equals(tenantCode)) {
            return SysResource.SCOPE_SHARED;
        }
        if (StringUtils.isBlank(tenantCode) || TenantCodes.DEFAULT.equals(tenantCode)) {
            return SysResource.SCOPE_PLATFORM;
        }
        return SysResource.SCOPE_TENANT;
    }

    private static String ownerTenant(String tenantCode) {
        if (StringUtils.isBlank(tenantCode)) {
            return TenantCodes.DEFAULT;
        }
        return tenantCode;
    }

    /** 仅用于避免 ai 包直接依赖 TenantUtil 常量语义。 */
    private static final class TenantCodes {
        private static final String PUBLIC = "public";
        private static final String DEFAULT = "default";
    }
}
