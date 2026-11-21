package com.nway.system.portal.service;

import com.nway.spring.jdbc.sql.fill.incrementer.IdWorker;
import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.portal.web.model.LowcodeMenuOperateRequest;
import com.nway.system.portal.web.model.LowcodeMenuSyncItem;
import com.nway.system.portal.web.model.LowcodeMenuSyncRequest;
import com.nway.system.portal.web.model.LowcodeMenuSyncResponse;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.openplatform.web.context.OpenAppContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.portal.entity.Menu;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.entity.Role;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 低代码平台菜单同步服务。
 * <p>
 * 低代码平台作为第三方系统（user_delegate 模式携带用户上下文），
 * 将其配置的页面/目录同步注册到菜单树（source=LOWCODE）。
 * 目标应用可为数据作用域租户的应用，或 public（公共租户）的共享应用；
 * 菜单 tenant_code 记<b>数据作用域租户</b>（共享应用下各租户菜单互相隔离）。
 * <p>
 * 核心语义：
 * <ul>
 *   <li>幂等 upsert：以 (appCode, externalId) 为键，重复发布=更新，非全量替换</li>
 *   <li>父级引用双轨制：parentMenuId 引用本系统已有菜单；externalParentId 引用同批次
 *       或历史批次已发布的 LOWCODE 节点；两者互斥，都空时新建挂应用根、更新保持原父级</li>
 *   <li>attachToAppRoot=true：显式挂应用根（新建/更新一致，更新会把原父级改为应用根），
 *       与上述两个父级字段互斥</li>
 *   <li>表单详情/编辑形态：visible=false 的 PAGE，可路由可授权但不进导航菜单</li>
 *   <li>自动授权：发布后按 grantRoleCodes 幂等创建 view 权限策略（否则新页面默认无人可见）</li>
 * </ul>
 */
@Service
public class LowcodeMenuService {

    private static final String SOURCE_LOWCODE = "LOWCODE";
    private static final String TYPE_DIR = "DIR";
    private static final String TYPE_PAGE = "PAGE";
    /**
     * 缺省渲染器：调用方未下发 renderType 时使用。
     * 选 wujie 的理由：跨域子应用默认走 shadow DOM 隔离，且不像 micro-app 的 search 路由模式
     * 会向宿主地址栏写参；与线上存量分布（wujie 占多数）一致，减少同一应用内 renderType 混杂。
     */
    private static final String DEFAULT_RENDER_TYPE = "wujie";
    private static final int MAX_PARENT_CHAIN_DEPTH = 100;

    @Autowired
    private MenuDao menuDao;
    @Autowired
    private AppDao appDao;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private PolicyService policyService;

    // ====== 查询 ======

    /**
     * 查询可发布的应用列表（供低代码平台选择"所属应用"）：
     * 数据作用域租户的应用 + public（公共租户）的共享应用。
     */
    public List<App> listPublishableApps() {
        LoginUser user = requireDelegateUser();
        return appDao.queryByStatus("using").stream()
                .filter(app -> user.getTenantCode().equals(app.getTenantCode())
                        || TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode()))
                .toList();
    }

    /**
     * 查询应用下 DIR/PAGE 菜单（供低代码平台选择"父级菜单"，根节点为应用本身）
     */
    public List<Menu> queryMenuTree(String appCode) {
        LoginUser user = requireDelegateUser();
        App app = requireAppInTenant(appCode, user);
        return menuDao.queryDirAndPageByApp(app.getAppCode());
    }

    // ====== 同步 ======

    /**
     * 菜单同步（幂等 upsert，整批校验通过后落库，任一失败整体回滚）。
     * <p>
     * 并发控制由调用方（Controller）在事务外持有跨实例分布式锁保证（见
     * {@link LowcodeMenuSyncLock}），配合 {@code sys_menu} 的活跃唯一索引
     * （{@code uidx_menu_active_external}）形成双重兜底，防止同应用并发发布对同一
     * externalId 重复插入。
     */
    @Transactional(rollbackFor = Exception.class)
    public LowcodeMenuSyncResponse sync(LowcodeMenuSyncRequest request) {
        LoginUser user = requireDelegateUser();
        App app = requireAppInTenant(request.getAppCode(), user);
        if (CollectionUtils.isEmpty(request.getItems())) {
            throw new NwaySystemException("菜单项列表不能为空");
        }

        // 1. 批内基础校验
        validateItems(request.getItems());

        // 2. upsert 命中查询 + 菜单id 预分配（新建项显式生成 id，父级解析不依赖 insert 回填）
        Set<String> externalIds = request.getItems().stream()
                .map(LowcodeMenuSyncItem::getExternalId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Menu> existingMap = menuDao.queryByAppAndExternalIds(app.getAppCode(), externalIds).stream()
                .collect(Collectors.toMap(Menu::getExternalId, m -> m, (a, b) -> a));
        Map<String, String> menuIdByExternalId = new LinkedHashMap<>();
        Map<String, LowcodeMenuSyncItem> itemByExternalId = new LinkedHashMap<>();
        for (LowcodeMenuSyncItem item : request.getItems()) {
            itemByExternalId.put(item.getExternalId(), item);
            Menu existing = existingMap.get(item.getExternalId());
            menuIdByExternalId.put(item.getExternalId(),
                    existing != null ? existing.getId() : IdWorker.getIdStr());
        }

        // 3. 拓扑排序（批内父子依赖 + 环拦截），确定落库顺序无关性下的解析顺序
        resolveBatchDependencies(request.getItems(), itemByExternalId);

        // 4. 解析每项最终 parentId（双轨制）
        Map<String, String> finalParentByExternalId =
                resolveFinalParents(request.getItems(), existingMap, menuIdByExternalId, app, user);

        // 5. 全图环检测（批内新父 + 批外 DB 现有父链）
        detectCycle(request.getItems(), menuIdByExternalId, finalParentByExternalId);

        // 6. upsert 落库
        LowcodeMenuSyncResponse response = new LowcodeMenuSyncResponse();
        Set<String> authorizedResourceCodes = new LinkedHashSet<>();
        for (LowcodeMenuSyncItem item : request.getItems()) {
            Menu existing = existingMap.get(item.getExternalId());
            String menuId = menuIdByExternalId.get(item.getExternalId());
            String finalParentId = finalParentByExternalId.get(item.getExternalId());

            Menu menu = existing != null ? existing : new Menu();
            menu.setId(menuId);
            menu.setName(item.getName());
            // type 为库表 NOT NULL 且无默认值，必须显式写入（DIR/PAGE）
            menu.setType(item.getType());
            menu.setUrl(item.getUrl());
            menu.setResourceCode(item.getResourceCode());
            menu.setRenderType(StringUtils.defaultIfBlank(item.getRenderType(), DEFAULT_RENDER_TYPE));
            menu.setIcon(item.getIcon());
            menu.setSequence(item.getSequence());
            // visible/isEnabled：仅在调用方显式提供时覆盖；缺省时更新保持现值、新建取 true。
            // 避免低代码未声明的字段把平台管理员手工设置的"停用/隐藏"在下次同步时重置。
            if (item.getVisible() != null) {
                menu.setVisible(item.getVisible());
            } else if (existing == null) {
                menu.setVisible(Boolean.TRUE);
            }
            if (item.getIsEnabled() != null) {
                menu.setIsEnabled(item.getIsEnabled());
            } else if (existing == null) {
                menu.setIsEnabled(Boolean.TRUE);
            }
            menu.setParentId(finalParentId);
            menu.setAppCode(app.getAppCode());
            menu.setSource(SOURCE_LOWCODE);
            menu.setExternalId(item.getExternalId());
            // 菜单归属数据作用域租户（而非应用租户）：共享应用（public）下各租户菜单互相隔离
            menu.setTenantCode(user.getTenantCode());

            if (existing != null) {
                menuDao.update(menu);
                response.addResult(item.getExternalId(), menuId, finalParentId,
                        LowcodeMenuSyncResponse.ResultItem.UPDATED);
            } else {
                menuDao.insert(menu);
                response.addResult(item.getExternalId(), menuId, finalParentId,
                        LowcodeMenuSyncResponse.ResultItem.CREATED);
            }

            if (TYPE_PAGE.equals(item.getType()) && !Boolean.FALSE.equals(menu.getVisible())
                    && StringUtils.isNotBlank(menu.getResourceCode())) {
                authorizedResourceCodes.add(menu.getResourceCode());
            }
        }

        // 7. 自动授权（告警不阻断发布）
        grantPolicies(request.getGrantRoleCodes(), user, app, authorizedResourceCodes, response);

        return response;
    }

    // ====== 删除 / 停用 ======

    /**
     * 删除菜单（仅 source=LOWCODE；存在子菜单时整批拒绝，防止低代码侧状态不一致）
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(LowcodeMenuOperateRequest request) {
        List<Menu> menus = loadOperateTargets(request);
        for (Menu menu : menus) {
            if (menuDao.countChildren(menu.getId()) > 0) {
                throw new NwaySystemException("菜单存在子菜单，不能删除（请先删除子菜单）：" + menu.getExternalId());
            }
        }
        for (Menu menu : menus) {
            menuDao.delete(menu.getId());
        }
    }

    /**
     * 停用菜单（保留配置，低代码页面下线场景；幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public void disable(LowcodeMenuOperateRequest request) {
        List<Menu> menus = loadOperateTargets(request);
        for (Menu menu : menus) {
            if (Boolean.TRUE.equals(menu.getIsEnabled())) {
                menu.setIsEnabled(false);
                menuDao.update(menu);
            }
        }
    }

    private List<Menu> loadOperateTargets(LowcodeMenuOperateRequest request) {
        LoginUser user = requireDelegateUser();
        App app = requireAppInTenant(request.getAppCode(), user);
        if (CollectionUtils.isEmpty(request.getExternalIds())) {
            throw new NwaySystemException("外部标识列表不能为空");
        }
        // 仅命中 LOWCODE 来源菜单，不影响平台管理员配置的菜单；未命中的忽略（幂等语义）
        return new ArrayList<>(menuDao.queryByAppAndExternalIds(app.getAppCode(), request.getExternalIds()).stream()
                .collect(Collectors.toMap(Menu::getExternalId, m -> m, (a, b) -> a)).values());
    }

    // ====== 校验与解析 ======

    /**
     * 批内基础校验：externalId 唯一、类型合法、字段完整性、父级字段互斥
     */
    private void validateItems(List<LowcodeMenuSyncItem> items) {
        Set<String> seenExternalIds = new HashSet<>();
        for (LowcodeMenuSyncItem item : items) {
            String ref = item.getExternalId();
            if (StringUtils.isBlank(ref)) {
                throw new NwaySystemException("菜单项 externalId 不能为空");
            }
            if (!seenExternalIds.add(ref)) {
                throw new NwaySystemException("菜单项 externalId 重复：" + ref);
            }
            if (StringUtils.isBlank(item.getName())) {
                throw new NwaySystemException("菜单名称不能为空：" + ref);
            }
            if (!TYPE_DIR.equals(item.getType()) && !TYPE_PAGE.equals(item.getType())) {
                throw new NwaySystemException("菜单类型仅支持 DIR/PAGE：" + ref);
            }
            if (StringUtils.isNotBlank(item.getParentMenuId())
                    && StringUtils.isNotBlank(item.getExternalParentId())) {
                throw new NwaySystemException("parentMenuId 与 externalParentId 互斥，只能选其一：" + ref);
            }
            if (Boolean.TRUE.equals(item.getAttachToAppRoot())
                    && (StringUtils.isNotBlank(item.getParentMenuId())
                    || StringUtils.isNotBlank(item.getExternalParentId()))) {
                throw new NwaySystemException("attachToAppRoot 与 parentMenuId/externalParentId 互斥：" + ref);
            }
            if (TYPE_DIR.equals(item.getType())) {
                if (StringUtils.isNotBlank(item.getUrl())) {
                    throw new NwaySystemException("目录类型不可携带 url：" + ref);
                }
            } else {
                if (StringUtils.isBlank(item.getUrl())) {
                    throw new NwaySystemException("页面类型必须提供 url：" + ref);
                }
                if (StringUtils.isBlank(item.getResourceCode())) {
                    throw new NwaySystemException("页面类型必须提供 resourceCode：" + ref);
                }
            }
        }
    }

    /**
     * 批内依赖拓扑排序（Kahn）：
     * externalParentId 指向批内项时父级须先解析；批内引用链成环在此拦截。
     * externalParentId 指向历史批次节点（批内不存在）不构成批内依赖，立即可处理。
     */
    private void resolveBatchDependencies(List<LowcodeMenuSyncItem> items,
                                          Map<String, LowcodeMenuSyncItem> itemByExternalId) {
        for (LowcodeMenuSyncItem item : items) {
            String parentExternalId = item.getExternalParentId();
            if (StringUtils.isNotBlank(parentExternalId) && !itemByExternalId.containsKey(parentExternalId)) {
                // 批外引用，稍后从 DB 校验；此处仅跳过批内依赖
                continue;
            }
            if (StringUtils.isNotBlank(parentExternalId)
                    && !TYPE_DIR.equals(itemByExternalId.get(parentExternalId).getType())) {
                throw new NwaySystemException("externalParentId 指向的批内项须为目录类型："
                        + item.getExternalId() + " -> " + parentExternalId);
            }
        }

        List<LowcodeMenuSyncItem> pending = new ArrayList<>(items);
        Set<String> resolved = new HashSet<>();
        while (!pending.isEmpty()) {
            List<LowcodeMenuSyncItem> ready = pending.stream()
                    .filter(item -> StringUtils.isBlank(item.getExternalParentId())
                            || !itemByExternalId.containsKey(item.getExternalParentId())
                            || resolved.contains(item.getExternalParentId()))
                    .toList();
            if (ready.isEmpty()) {
                throw new NwaySystemException("菜单项 externalParentId 引用链成环");
            }
            ready.forEach(item -> {
                resolved.add(item.getExternalId());
                pending.remove(item);
            });
        }
    }

    /**
     * 解析每项最终 parentId（双轨制语义）：
     * <ul>
     *   <li>parentMenuId → 本系统菜单 id（校验存在、同应用、DIR）</li>
     *   <li>externalParentId → 批内项预分配 id，或历史批次 LOWCODE 节点 id（校验存在、DIR）</li>
     *   <li>attachToAppRoot=true → 应用根（空串），新建/更新一致</li>
     *   <li>都空 → 更新保持原父级不变；新建挂应用根（空串）</li>
     * </ul>
     */
    private Map<String, String> resolveFinalParents(List<LowcodeMenuSyncItem> items,
                                                    Map<String, Menu> existingMap,
                                                    Map<String, String> menuIdByExternalId,
                                                    App app, LoginUser user) {
        Map<String, String> finalParentByExternalId = new LinkedHashMap<>();
        // 批外父级锚点：externalParentId / parentMenuId → DB 菜单（懒加载缓存）
        Map<String, Menu> externalAnchorCache = new LinkedHashMap<>();

        for (LowcodeMenuSyncItem item : items) {
            String finalParentId;
            if (Boolean.TRUE.equals(item.getAttachToAppRoot())) {
                // 显式挂应用根：与"更新保持原父级"区分，用于把历史菜单从原父级移出
                finalParentId = "";
            } else if (StringUtils.isNotBlank(item.getParentMenuId())) {
                Menu parent = loadExternalAnchor(item.getParentMenuId(), externalAnchorCache, app, user);
                if (!TYPE_DIR.equals(parent.getType())) {
                    throw new NwaySystemException("parentMenuId 指向的菜单须为目录类型："
                            + item.getExternalId() + " -> " + item.getParentMenuId());
                }
                finalParentId = parent.getId();
            } else if (StringUtils.isNotBlank(item.getExternalParentId())) {
                String parentId;
                if (menuIdByExternalId.containsKey(item.getExternalParentId())) {
                    // 批内引用（含更新项），使用预分配 id
                    parentId = menuIdByExternalId.get(item.getExternalParentId());
                } else {
                    // 历史批次 LOWCODE 节点
                    Menu parent = loadLowcodeAnchor(item.getExternalParentId(), externalAnchorCache, app, user);
                    parentId = parent.getId();
                }
                finalParentId = parentId;
            } else {
                Menu existing = existingMap.get(item.getExternalId());
                // 更新保持原父级不变；新建挂应用根
                finalParentId = existing != null ? existing.getParentId() : "";
            }
            finalParentByExternalId.put(item.getExternalId(), finalParentId);
        }
        return finalParentByExternalId;
    }

    /**
     * 全图环检测：批内项用解析后的新父，批外节点用 DB 现有父链（批外状态不变）。
     * 从每项沿父链上溯，回到自身 id 即为环。
     */
    private void detectCycle(List<LowcodeMenuSyncItem> items,
                             Map<String, String> menuIdByExternalId,
                             Map<String, String> finalParentByExternalId) {
        // 批内 menuId → 项（沿链上溯时识别批内节点）
        Map<String, LowcodeMenuSyncItem> itemByMenuId = new LinkedHashMap<>();
        for (LowcodeMenuSyncItem item : items) {
            itemByMenuId.put(menuIdByExternalId.get(item.getExternalId()), item);
        }
        // 环检测用的父链函数（不校验外部锚点合法性，已在 resolveFinalParents 完成）
        Map<String, Menu> anchorCache = new LinkedHashMap<>();

        for (LowcodeMenuSyncItem item : items) {
            String selfId = menuIdByExternalId.get(item.getExternalId());
            String cursor = finalParentByExternalId.get(item.getExternalId());
            int depth = 0;
            while (StringUtils.isNotBlank(cursor) && depth++ < MAX_PARENT_CHAIN_DEPTH) {
                if (cursor.equals(selfId)) {
                    throw new NwaySystemException("菜单挂载关系成环：" + item.getExternalId());
                }
                LowcodeMenuSyncItem batchItem = itemByMenuId.get(cursor);
                if (batchItem != null) {
                    cursor = finalParentByExternalId.get(batchItem.getExternalId());
                } else {
                    Menu dbMenu = anchorCache.computeIfAbsent(cursor, menuDao::getById);
                    cursor = dbMenu != null ? dbMenu.getParentId() : null;
                }
            }
        }
    }

    /**
     * 加载外部锚点菜单（parentMenuId 引用），校验存在且属于目标应用
     */
    private Menu loadExternalAnchor(String menuId, Map<String, Menu> cache, App app, LoginUser user) {
        Menu parent = cache.computeIfAbsent(menuId, menuDao::getById);
        if (parent == null) {
            throw new NwaySystemException("父级菜单不存在：" + menuId);
        }
        if (!app.getAppCode().equals(parent.getAppCode())) {
            throw new NwaySystemException("父级菜单不属于目标应用：" + menuId);
        }
        return parent;
    }

    /**
     * 加载历史批次 LOWCODE 锚点菜单（externalParentId 引用批外节点），校验存在且为 DIR
     */
    private Menu loadLowcodeAnchor(String externalId, Map<String, Menu> cache, App app, LoginUser user) {
        Menu parent = cache.computeIfAbsent(externalId, id -> {
            List<Menu> found = menuDao.queryByAppAndExternalIds(app.getAppCode(), List.of(id));
            return found.isEmpty() ? null : found.get(0);
        });
        if (parent == null) {
            throw new NwaySystemException("externalParentId 指向的菜单不存在（须为同应用下已发布的 LOWCODE 菜单或同批次项）："
                    + externalId);
        }
        if (!TYPE_DIR.equals(parent.getType())) {
            throw new NwaySystemException("externalParentId 指向的菜单须为目录类型：" + externalId);
        }
        return parent;
    }

    // ====== 自动授权 ======

    /**
     * 为本次发布的可见页面授予角色 view 策略。
     * 无效角色仅告警不阻断；策略幂等（已存在同资源策略则跳过）。
     */
    private void grantPolicies(List<String> grantRoleCodes, LoginUser user, App app,
                               Set<String> resourceCodes, LowcodeMenuSyncResponse response) {
        if (CollectionUtils.isEmpty(grantRoleCodes) || resourceCodes.isEmpty()) {
            return;
        }
        // 角色编码的唯一键是 (role_code, app_code, tenant_code)：同名编码可能跨租户/公共租户存在。
        // 先收敛到代理用户租户再按 code 建索引；否则合并函数会静默取到别租户的角色，
        // 使同租户下的合法角色被“不属于当前租户”误跳过。同租户内仍重复属数据异常，显式抛出。
        Map<String, Role> roleByCode = roleDao.queryByCodes(grantRoleCodes).stream()
                .filter(r -> user.getTenantCode().equals(r.getTenantCode()))
                .collect(Collectors.toMap(Role::getRoleCode, r -> r, (a, b) -> {
                    throw new NwaySystemException("角色编码在当前租户内重复，无法唯一定位：" + a.getRoleCode());
                }));
        List<Role> validRoles = new ArrayList<>();
        for (String roleCode : grantRoleCodes) {
            Role role = roleByCode.get(roleCode);
            if (role == null) {
                response.addWarning("角色不存在或不属于当前租户，已跳过授权：" + roleCode);
            } else if (!canReceivePolicy(role, app)) {
                response.addWarning("目标应用不在角色租户的可授权范围内，已跳过授权：" + roleCode);
            } else {
                validRoles.add(role);
            }
        }
        for (Role role : validRoles) {
            for (String resourceCode : resourceCodes) {
                policyService.ensureRoleResourcePolicy(role, app.getAppCode(), resourceCode);
            }
        }
    }

    /**
     * 角色能否接收目标应用的发布授权。
     * <p>
     * 角色 app_code 只是「主应用」：页面常发布在租户自己的低代码应用下，因此同租户跨应用、
     * 以及 public 共享应用都应允许；其他租户的私有应用对本租户用户不可达，必须跳过（fail-closed）。
     * 与 {@code PolicyService#saveRolePolicies} 的应用范围规则保持一致。
     */
    private boolean canReceivePolicy(Role role, App app) {
        if ("*".equals(role.getAppCode()) || app.getAppCode().equals(role.getAppCode())) {
            return true;
        }
        return role.getTenantCode() != null && (role.getTenantCode().equals(app.getTenantCode())
                || TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode()));
    }

    // ====== 上下文校验 ======

    /**
     * 低代码菜单同步仅支持 user_delegate 授权模式：
     * 租户上下文取自代理用户（满足"选择其所属租户下的应用"），不支持应用身份直连。
     */
    private LoginUser requireDelegateUser() {
        OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
        if (appInfo == null || StringUtils.isBlank(appInfo.getEmployeeId())) {
            throw new NwaySystemException("低代码菜单接口仅支持 user_delegate 授权模式（需携带代理用户上下文）");
        }
        LoginUser user = LoginUserContext.current();
        if (user == null || StringUtils.isBlank(user.getTenantCode())) {
            throw new NwaySystemException("无法获取代理用户租户信息");
        }
        return user;
    }

    /**
     * 校验应用存在、已启用，且属于代理用户租户或为 public（公共租户）共享应用
     * （租户隔离已由 TenantPermissionStrategy 自动过滤，此处显式校验兜底 fail-closed）
     */
    private App requireAppInTenant(String appCode, LoginUser user) {
        if (StringUtils.isBlank(appCode)) {
            throw new NwaySystemException("应用标识不能为空");
        }
        App app = appDao.getByCode(appCode);
        if (app == null) {
            throw new NwaySystemException("应用不存在或不在当前租户内：" + appCode);
        }
        if (!user.getTenantCode().equals(app.getTenantCode())
                && !TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode())) {
            throw new NwaySystemException("应用不属于当前租户，且非公共共享应用：" + appCode);
        }
        if (!"using".equals(app.getStatus())) {
            throw new NwaySystemException("应用已停用：" + appCode);
        }
        return app;
    }
}
