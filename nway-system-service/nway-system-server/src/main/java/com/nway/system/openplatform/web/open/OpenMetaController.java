package com.nway.system.openplatform.web.open;

import com.nway.system.openplatform.web.model.OpenResourceCatalogVo;
import com.nway.system.common.web.Result;
import com.nway.system.openplatform.web.context.OpenAppContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.portal.entity.Menu;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 开放平台：应用资源目录 API。
 * <p>
 * 返回<b>当前 Token 所属应用</b>的资源清单（资源标识 + 可用动作），资源声明载体为
 * {@code sys_menu}：PAGE 的 {@code resource_code} 即资源，其下 BTN 的 {@code action} 即动作
 * （与「角色策略」配置界面的资源来源一致，故目录内容与可配置项天然对齐）。
 * <p>
 * <b>鉴权</b>：仅需有效应用 Token，不再要求 scope —— 否则"查询自己有哪些权限"
 * 需要先拥有该权限，构成循环；且目录描述的是调用方自身，非他人数据。
 * 见 {@code OpenApiInterceptor#resolveScope} 对 {@code meta} 模块的豁免。
 *
 * @author nway
 */
@RestController
@RequestMapping("/open/meta")
public class OpenMetaController {

    @Autowired
    private MenuDao menuDao;

    /**
     * 查询当前应用的资源目录。
     */
    @GetMapping("catalog")
    public Result<OpenResourceCatalogVo> catalog() {
        OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
        // 上下文缺失说明拦截器链路异常：fail-closed，不返回任何资源（而非返回全量）
        if (appInfo == null || StringUtils.isBlank(appInfo.getAppCode())) {
            return Result.ok(emptyCatalog(null));
        }
        String appCode = appInfo.getAppCode();

        // 租户范围与「角色策略」配置界面一致：作用域租户 + public 共享租户
        Set<String> tenants = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(appInfo.getTenantCode())) {
            tenants.add(appInfo.getTenantCode());
        }
        tenants.add(TenantUtil.PUBLIC_TENANT_CODE);

        List<Menu> menus = menuDao.queryByAppInTenants(appCode, tenants).stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsEnabled()))
                .toList();

        OpenResourceCatalogVo vo = new OpenResourceCatalogVo();
        vo.setAppCode(appCode);
        vo.setResources(buildResources(menus));
        return Result.ok(vo);
    }

    private OpenResourceCatalogVo emptyCatalog(String appCode) {
        OpenResourceCatalogVo vo = new OpenResourceCatalogVo();
        vo.setAppCode(appCode);
        vo.setResources(List.of());
        return vo;
    }

    /**
     * 由菜单构建资源目录：PAGE → 资源，其下 BTN → 动作。
     * <p>
     * 动作按 BTN 的父级链归集到最近的 PAGE（与前端 {@code rolePolicy.vue#actionsFor} 同一口径），
     * 允许 BTN 与 PAGE 之间隔 DIR 层。
     */
    private List<OpenResourceCatalogVo.Resource> buildResources(List<Menu> menus) {
        Map<String, Menu> byId = new LinkedHashMap<>();
        for (Menu m : menus) {
            if (m.getId() != null) {
                byId.put(m.getId(), m);
            }
        }

        // 资源：PAGE 且 resourceCode 非空（按出现顺序去重）
        Map<String, OpenResourceCatalogVo.Resource> resources = new LinkedHashMap<>();
        for (Menu m : menus) {
            if ("PAGE".equals(m.getType()) && StringUtils.isNotBlank(m.getResourceCode())
                    && !resources.containsKey(m.getResourceCode())) {
                OpenResourceCatalogVo.Resource res = new OpenResourceCatalogVo.Resource();
                res.setResourceCode(m.getResourceCode());
                res.setName(m.getName());
                res.setActions(new ArrayList<>());
                resources.put(m.getResourceCode(), res);
            }
        }

        // 动作：BTN 归集到所属 PAGE 资源（同名动作去重）
        Map<String, Set<String>> seenActions = new LinkedHashMap<>();
        for (Menu m : menus) {
            if (!"BTN".equals(m.getType()) || StringUtils.isBlank(m.getAction())) {
                continue;
            }
            Menu page = findParentPage(m, byId);
            if (page == null || StringUtils.isBlank(page.getResourceCode())) {
                continue;
            }
            OpenResourceCatalogVo.Resource res = resources.get(page.getResourceCode());
            if (res == null) {
                continue;
            }
            Set<String> seen = seenActions.computeIfAbsent(page.getResourceCode(), k -> new LinkedHashSet<>());
            if (!seen.add(m.getAction())) {
                continue;
            }
            OpenResourceCatalogVo.Action action = new OpenResourceCatalogVo.Action();
            action.setAction(m.getAction());
            action.setName(m.getName());
            res.getActions().add(action);
        }

        return new ArrayList<>(resources.values());
    }

    /**
     * 沿父级链查找最近的 PAGE（跳过 DIR 层）。
     */
    private Menu findParentPage(Menu btn, Map<String, Menu> byId) {
        String parentId = btn.getParentId();
        // 防御环引用：菜单层级有限，用计数上限兜底
        int guard = 0;
        while (StringUtils.isNotBlank(parentId) && guard++ < 32) {
            Menu parent = byId.get(parentId);
            if (parent == null) {
                return null;
            }
            if ("PAGE".equals(parent.getType())) {
                return parent;
            }
            parentId = parent.getParentId();
        }
        return null;
    }
}