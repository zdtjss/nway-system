package com.nway.system.authorization.service.grant;

import com.alibaba.fastjson2.JSON;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.authorization.dao.GrantTemplateDao;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.entity.SysGrantTemplate;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.entity.Role;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 授权模板（租户开通套餐）。
 * <p>
 * 模板 = 若干角色 + 每个角色的授权清单；应用到目标租户时：
 * 角色不存在则创建，存在则**替换**其授权（幂等）。
 */
@Service
public class GrantTemplateService {

    private final GrantTemplateDao templateDao;
    private final RoleDao roleDao;
    private final GrantCommandService commandService;

    public GrantTemplateService(GrantTemplateDao templateDao, RoleDao roleDao,
                                GrantCommandService commandService) {
        this.templateDao = templateDao;
        this.roleDao = roleDao;
        this.commandService = commandService;
    }

    public List<SysGrantTemplate> list() {
        return templateDao.list();
    }

    @Transactional(rollbackFor = Exception.class)
    public String save(SysGrantTemplate template) {
        if (template == null || StringUtils.isBlank(template.getTemplateCode())) {
            throw new NwaySystemException("模板编码不能为空");
        }
        if (StringUtils.isBlank(template.getName())) {
            throw new NwaySystemException("模板名称不能为空");
        }
        if (StringUtils.isBlank(template.getItemsJson())) {
            throw new NwaySystemException("模板至少需要一个角色项");
        }
        // 结构校验：能解析且每个角色项有 roleCode
        List<TemplateItem> items = parseItems(template.getItemsJson());
        if (items.isEmpty()) {
            throw new NwaySystemException("模板至少需要一个角色项");
        }
        SysGrantTemplate existing = StringUtils.isBlank(template.getId())
                ? templateDao.getByCode(template.getTemplateCode())
                : templateDao.getById(template.getId());
        if (existing != null) {
            template.setId(existing.getId());
            template.setStatus(StringUtils.defaultIfBlank(template.getStatus(), existing.getStatus()));
            templateDao.updateById(template);
        } else {
            template.setStatus(StringUtils.defaultIfBlank(template.getStatus(), "ACTIVE"));
            templateDao.insert(template);
        }
        return template.getId();
    }

    public void delete(String id) {
        templateDao.deleteById(id);
    }

    /**
     * 应用模板到租户：按角色创建/更新，并替换角色授权。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApplyResult apply(String templateId, String tenantCode, String reason) {
        TenantUtil.assertAdminCanManageTenant(tenantCode);
        SysGrantTemplate template = templateDao.getById(templateId);
        if (template == null) {
            throw new NwaySystemException("模板不存在：" + templateId);
        }
        List<TemplateItem> items = parseItems(template.getItemsJson());
        List<Role> existing = roleDao.queryByTenantCodes(List.of(tenantCode));
        int createdRoles = 0;
        int grantCount = 0;
        for (TemplateItem item : items) {
            if (item == null || StringUtils.isBlank(item.roleCode())) {
                throw new NwaySystemException("模板角色项缺少 roleCode");
            }
            String appCode = StringUtils.defaultIfBlank(item.appCode(), template.getAppCode());
            Role role = existing.stream()
                    .filter(r -> item.roleCode().equals(r.getRoleCode())
                            && Objects.equals(appCode, r.getAppCode()))
                    .findFirst()
                    .orElse(null);
            if (role == null) {
                role = new Role();
                role.setRoleCode(item.roleCode());
                role.setRoleName(StringUtils.defaultIfBlank(item.roleName(), item.roleCode()));
                role.setAppCode(appCode);
                role.setTenantCode(tenantCode);
                role.setIsDeleted(Boolean.FALSE);
                roleDao.insert(role);
                createdRoles++;
            }
            List<SysGrant> grants = toGrants(appCode, item.grants());
            commandService.replaceRoleGrants(role.getId(), grants, reason);
            grantCount += grants.size();
        }
        return new ApplyResult(items.size(), createdRoles, grantCount);
    }

    private List<TemplateItem> parseItems(String json) {
        try {
            List<TemplateItem> items = JSON.parseArray(json, TemplateItem.class);
            return items == null ? List.of() : items;
        } catch (RuntimeException e) {
            throw new NwaySystemException("模板 itemsJson 解析失败：" + e.getMessage());
        }
    }

    private List<SysGrant> toGrants(String defaultApp, List<GrantItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return List.of();
        }
        List<SysGrant> grants = new ArrayList<>();
        for (GrantItem item : items) {
            SysGrant grant = new SysGrant();
            grant.setAppCode(StringUtils.defaultIfBlank(item.appCode(), defaultApp));
            grant.setResourceCode(item.resourceCode());
            grant.setActions(JSON.toJSONString(item.actions() == null ? List.of() : item.actions()));
            grant.setEffect(StringUtils.defaultIfBlank(item.effect(), SysGrant.EFFECT_ALLOW));
            grant.setDataScope(StringUtils.defaultIfBlank(item.dataScope(), SysGrant.SCOPE_SELF));
            grant.setScopeDeptIds(item.scopeDeptIds() == null
                    ? null : JSON.toJSONString(item.scopeDeptIds()));
            grants.add(grant);
        }
        return grants;
    }

    public record TemplateItem(String roleCode, String roleName, String appCode, List<GrantItem> grants) {
    }

    public record GrantItem(String appCode, String resourceCode, List<String> actions,
                            String dataScope, String effect, List<String> scopeDeptIds) {
    }

    public record ApplyResult(int roleCount, int createdRoles, int grantCount) {
    }
}
