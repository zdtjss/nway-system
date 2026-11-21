package com.nway.system.portal.service;

import com.nway.system.authorization.service.decision.AuthzDecisionService;
import com.nway.system.authorization.service.decision.SubjectContext;
import com.nway.system.authorization.service.decision.SubjectContextResolver;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.portal.entity.Menu;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import com.nway.system.portal.web.model.IndexPageMenuVo;
import com.nway.system.portal.web.model.MenuTreeVo;
import com.nway.system.portal.web.model.UserMenuVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单服务（统一权限策略版）。
 * <p>
 * 菜单不再承载权限语义，其可见性由授权决策内核推导：
 * <ul>
 *   <li>PAGE：该资源上任意动作放行 → 可见（DENY 优先、租户收敛）</li>
 *   <li>BTN：不进导航（仅承载动作授权）</li>
 *   <li>DIR：下级有可见菜单 → 可见</li>
 * </ul>
 */
@Slf4j
@Service
public class MenuService {

    /**
     * 资源归属租户：平台内置资源（default）与共享应用资源（public）。
     * 其下菜单对所有租户只读可见（迁移 30 的 {@code sys_menu.tenant_code} 语义）。
     */
    private static final List<String> SHARED_MENU_OWNER_TENANTS =
            List.of(TenantUtil.DEFAULT_TENANT_CODE, TenantUtil.PUBLIC_TENANT_CODE);

    @Autowired
    private MenuDao menuDao;
    @Autowired
    private AppDao appDao;
    @Autowired
    private ResourceDao resourceDao;

    @Autowired
    private AuthzDecisionService decisionService;
    @Autowired
    private SubjectContextResolver subjectContextResolver;
    @Autowired
    @Lazy
    private MenuFavoriteService favoriteService;

    // ====== 菜单 CRUD ======

    @Transactional(rollbackFor = Exception.class)
    public void save(Menu menu) {
        if (StringUtils.isBlank(menu.getName())) {
            throw new NwaySystemException("菜单名称不能为空");
        }
        if (StringUtils.isBlank(menu.getAppCode())) {
            throw new NwaySystemException("应用标识不能为空");
        }
        // 从应用继承租户
        App app = appDao.getByCode(menu.getAppCode());
        if (app != null && StringUtils.isNotBlank(app.getTenantCode())) {
            menu.setTenantCode(app.getTenantCode());
        }
        TenantUtil.assertAdminCanManageTenant(menu.getTenantCode());

        // 导航只引用资源：资源目录是权限的唯一事实源
        bindResource(menu);

        if (menu.getIsEnabled() == null) {
            menu.setIsEnabled(true);
        }

        if (StringUtils.isBlank(menu.getId())) {
            if (menu.getSource() == null) {
                menu.setSource(Menu.SOURCE_PLATFORM);
            }
            menuDao.insert(menu);
        } else {
            // 编辑：source 为系统维护字段（PLATFORM/APP/LOWCODE），以库中原值为准。
            // 若被改写为 PLATFORM，低代码同步（source=LOWCODE 过滤）与子应用自注册
            // （deleteByAppAndSource 按 source 删除）都将查不到原记录，导致重复插入菜单
            Menu existing = menuDao.getById(menu.getId());
            if (existing != null) {
                menu.setSource(existing.getSource());
            }
            menuDao.update(menu);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Menu menu = menuDao.getById(id);
        if (menu == null) {
            return;
        }
        TenantUtil.assertAdminCanManageTenant(menu.getTenantCode());
        if (menuDao.countChildren(id) > 0) {
            throw new NwaySystemException("存在子菜单，不能删除");
        }
        menuDao.delete(id);
    }

    /**
     * 菜单引用资源：resource_id 为权威关联；resource_code 作为派生的兼容缓存。
     * 支持两个方向：只给编码时回填 id；只给 id 时反填编码。
     */
    private void bindResource(Menu menu) {
        if (StringUtils.isBlank(menu.getResourceCode()) && StringUtils.isNotBlank(menu.getResourceId())) {
            SysResource byId = resourceDao.getById(menu.getResourceId());
            if (byId != null) {
                menu.setResourceCode(byId.getResourceCode());
                if (StringUtils.isBlank(menu.getAppCode())) {
                    menu.setAppCode(byId.getAppCode());
                }
            }
            return;
        }
        if (StringUtils.isBlank(menu.getResourceCode())) {
            menu.setResourceId(null);
            return;
        }
        SysResource resource = resourceDao.getByAppAndCode(menu.getAppCode(), menu.getResourceCode());
        menu.setResourceId(resource != null ? resource.getId() : null);
    }

    public Menu getById(String id) {
        return menuDao.getById(id);
    }

    /**
     * 管理后台菜单列表
     */
    public List<Menu> queryForAdmin(String appCode, String parentId, String name) {
        return menuDao.queryForAdmin(appCode, parentId, name);
    }

    /**
     * 按应用查询菜单（策略配置弹窗用）。
     * <p>
     * 传入 targetTenantCode（目标角色的租户）时只返回「该租户 + public 共享租户」的菜单：
     * 其他租户的菜单即便被勾选也无法生效（租户隔离会过滤掉），不应出现在可选项里。
     * 不传时退化为全量查询，保持原行为。
     */
    public List<Menu> queryByApp(String appCode, String targetTenantCode) {
        if (StringUtils.isBlank(targetTenantCode)) {
            return queryForAdmin(appCode, null, null);
        }
        List<Menu> menus = new ArrayList<>(menuDao.queryByAppInTenants(appCode,
                List.of(targetTenantCode, TenantUtil.PUBLIC_TENANT_CODE)));
        // 资源归属租户（default/public）的平台菜单对所有租户可授权，必须出现在候选集里，
        // 否则租户管理员无法为自有角色配置平台功能权限。低代码页面菜单记的是「页面租户」，
        // 只在页面所属租户内可见，不进入其他租户的候选。
        Set<String> ids = menus.stream().map(Menu::getId).collect(Collectors.toSet());
        for (Menu menu : sharedMenus(appCode)) {
            if (menu.isResourceOwnerTenant() && ids.add(menu.getId())) {
                menus.add(menu);
            }
        }
        return menus;
    }

    /**
     * 读取资源归属租户（default/public）的启用菜单。
     * <p>
     * 必须在 {@link TenantUtil#callAsSystem} 下查询：菜单行级隔离只并入 public，
     * 不加系统上下文时 default 的平台菜单（应用/角色/用户等）会被过滤掉，
     * 导致新租户既看不到平台导航、也无法在策略配置里选中平台资源。
     */
    private List<Menu> sharedMenus(String appCode) {
        return TenantUtil.callAsSystem(() -> StringUtils.isBlank(appCode)
                ? menuDao.queryEnabledByTenants(SHARED_MENU_OWNER_TENANTS)
                : menuDao.queryByAppInTenants(appCode, SHARED_MENU_OWNER_TENANTS));
    }

    // ====== 用户菜单树（登录后调用） ======

    /**
     * 构建当前用户的可见菜单树。
     * <p>
     * 根据用户的合并策略推导菜单可见性，按应用分组构建树。
     */
    public UserMenuVo listUserMenu() {
        LoginUser loginUser = LoginUserContext.current();
        if (loginUser == null) {
            return new UserMenuVo();
        }

        // 获取全部启用菜单：本租户（含 public 读共享）+ 资源归属租户的共享菜单
        List<Menu> allMenus = loadNavigableMenus();
        if (CollectionUtils.isEmpty(allMenus)) {
            return new UserMenuVo();
        }
    
        // 导航菜单只由 DIR/PAGE 构成：BTN 仅承载动作授权，从不进入导航。
        // 隐藏页面（visible=false，低代码表单详情/编辑形态）不进导航菜单，但可路由、权限策略仍生效
        List<Menu> navMenus = allMenus.stream()
                .filter(m -> !"BTN".equals(m.getType()))
                .filter(m -> !("PAGE".equals(m.getType()) && Boolean.FALSE.equals(m.getVisible())))
                .toList();
    
        // 超管：全部可见
        if (loginUser.isSuperAdmin()) {
            return buildUserMenuVo(navMenus, loginUser);
        }
    
        // 菜单可见性统一走决策内核（与接口/数据权限同一套 DENY 优先 + 租户收敛）：
        // 一次加载授权、N 次内存判定；PAGE 用“任意动作放行”，与旧“资源在策略中存在”语义对齐。
        SubjectContext subject = subjectContextResolver.from(loginUser);
        List<AuthzDecisionService.Check> checks = navMenus.stream()
                .filter(m -> "PAGE".equals(m.getType()))
                .map(m -> new AuthzDecisionService.Check(m.getId(), m.getAppCode(),
                        m.getResourceCode(), null))
                .toList();
        Set<String> visibleMenuIds = new LinkedHashSet<>(decisionService.decideBatch(subject, checks));
    
        // DIR：下级有可见菜单则可见（递归向上）
        Set<String> dirIds = new LinkedHashSet<>();
        for (Menu menu : navMenus) {
            if (visibleMenuIds.contains(menu.getId()) && StringUtils.isNotBlank(menu.getParentId())) {
                propagateParentVisibility(menu.getParentId(), navMenus, dirIds);
            }
        }
        visibleMenuIds.addAll(dirIds);
    
        List<Menu> visibleMenus = navMenus.stream()
                .filter(m -> visibleMenuIds.contains(m.getId()))
                .toList();
    
        return buildUserMenuVo(visibleMenus, loginUser);
    }

    /**
     * 加载当前用户可导航的菜单：本租户（行级隔离自动并入 public 读共享）+ 资源归属租户的共享菜单。
     * <p>
     * 平台内置资源归 default、共享应用资源归 public，对所有租户只读可见；这是
     * {@code sys_menu.tenant_code} 的既定语义（迁移 30），也是新租户“开箱即有平台导航”的基础。
     * 低代码页面菜单（source=LOWCODE）记的是「页面租户」，只在页面所属租户内可见。
     */
    private List<Menu> loadNavigableMenus() {
        List<Menu> menus = new ArrayList<>(menuDao.queryAllEnabled());
        Set<String> ids = menus.stream().map(Menu::getId).collect(Collectors.toSet());
        for (Menu menu : sharedMenus(null)) {
            if (menu.isResourceOwnerTenant() && ids.add(menu.getId())) {
                menus.add(menu);
            }
        }
        return menus;
    }

    /**
     * 递归向上标记父级 DIR 可见
     */
    private void propagateParentVisibility(String parentId, List<Menu> allMenus, Set<String> dirIds) {
        if (StringUtils.isBlank(parentId) || dirIds.contains(parentId)) {
            return;
        }
        for (Menu menu : allMenus) {
            if (menu.getId().equals(parentId)) {
                dirIds.add(menu.getId());
                if (StringUtils.isNotBlank(menu.getParentId())) {
                    propagateParentVisibility(menu.getParentId(), allMenus, dirIds);
                }
                break;
            }
        }
    }

    /**
     * 构建 UserMenuVo（按应用分组 + 树结构）
     */
    private UserMenuVo buildUserMenuVo(List<Menu> menus, LoginUser loginUser) {
        UserMenuVo vo = new UserMenuVo();

        // 获取涉及的应用信息
        Set<String> appCodes = menus.stream().map(Menu::getAppCode).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Map<String, App> appMap = appDao.queryByCodes(appCodes);

        // 按应用分组
        Map<String, List<Menu>> menusByApp = menus.stream()
                .collect(Collectors.groupingBy(Menu::getAppCode));

        List<MenuTreeVo> treeVos = new ArrayList<>();
        for (Map.Entry<String, List<Menu>> entry : menusByApp.entrySet()) {
            App app = appMap.get(entry.getKey());
            List<MenuTreeVo> appTree = buildTree(entry.getValue(), app);

            MenuTreeVo moduleNode = new MenuTreeVo();
            moduleNode.setName(app != null ? app.getAppName() : entry.getKey());
            moduleNode.setChildren(appTree);
            moduleNode.setType("MODULE");
            treeVos.add(moduleNode);
        }
        vo.setMenuList(treeVos);

        // 个人常用（含用户设置的首页）
        IndexPageMenuVo fav = favoriteService.listFavByUser(loginUser.getEmployeeId());
        vo.setIndex(fav.getIndex());
        vo.setFavorite(fav.getFavorite());
        vo.setSys(List.of());

        return vo;
    }

    /**
     * 构建单应用内的菜单树
     */
    private List<MenuTreeVo> buildTree(List<Menu> menus, App app) {
        // URL 拼接
        String host = (app != null && StringUtils.isNotBlank(app.getHost())) ? app.getHost() : "";
        for (Menu menu : menus) {
            if (StringUtils.isNotBlank(menu.getUrl())) {
                String url = menu.getUrl();
                if (!url.startsWith("http")) {
                    if (host.endsWith("/") || url.startsWith("/")) {
                        url = host + url;
                    } else {
                        url = host + "/" + url;
                    }
                    menu.setUrl(url);
                }
            }
        }

        // 按 parentId 分组
        Map<String, List<Menu>> byParent = menus.stream()
                .collect(Collectors.groupingBy(m -> StringUtils.isNotBlank(m.getParentId()) ? m.getParentId() : ""));

        // 顶层菜单
        List<Menu> roots = byParent.getOrDefault("", List.of());
        List<MenuTreeVo> tree = new ArrayList<>();
        for (Menu root : roots.stream().sorted(Comparator.comparing(m -> m.getSequence() != null ? m.getSequence() : 0)).toList()) {
            MenuTreeVo node = toTreeVo(root);
            fillChildren(node, byParent);
            tree.add(node);
        }
        return tree;
    }

    private void fillChildren(MenuTreeVo node, Map<String, List<Menu>> byParent) {
        List<Menu> children = byParent.getOrDefault(node.getId(), List.of());
        if (children.isEmpty()) {
            return;
        }
        List<MenuTreeVo> childVos = children.stream()
                .sorted(Comparator.comparing(m -> m.getSequence() != null ? m.getSequence() : 0))
                .map(m -> {
                    MenuTreeVo vo = toTreeVo(m);
                    // BTN 继承父 PAGE 的 resourceCode（供前端 v-permission 使用）
                    if ("BTN".equals(m.getType()) && StringUtils.isBlank(vo.getResourceCode())
                            && StringUtils.isNotBlank(node.getResourceCode())) {
                        vo.setResourceCode(node.getResourceCode());
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        childVos.forEach(child -> fillChildren(child, byParent));
        node.setChildren(childVos);
    }

    private MenuTreeVo toTreeVo(Menu menu) {
        MenuTreeVo vo = new MenuTreeVo();
        vo.setId(menu.getId());
        vo.setName(menu.getName());
        vo.setUrl(menu.getUrl());
        vo.setIcon(menu.getIcon());
        vo.setType(menu.getType());
        vo.setRenderType(menu.getRenderType());
        vo.setResourceCode(menu.getResourceCode());
        vo.setAction(menu.getAction());
        vo.setTenantCode(menu.getTenantCode());
        return vo;
    }
}
