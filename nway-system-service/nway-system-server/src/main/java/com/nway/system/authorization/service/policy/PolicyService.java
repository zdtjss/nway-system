package com.nway.system.authorization.service.policy;

import com.alibaba.fastjson2.JSON;
import com.nway.system.authorization.service.grant.AuthzVersionService;
import com.nway.system.authorization.service.grant.GrantActionAdvisor;
import com.nway.system.authorization.service.grant.GrantCacheService;
import com.nway.system.authorization.domain.MergedPolicy;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.authorization.dao.PermissionPolicyDao;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.PermissionPolicy;
import com.nway.system.identity.dao.SysPositionDao;
import com.nway.system.identity.dao.SysUserPositionDao;
import com.nway.system.identity.entity.SysPosition;
import com.nway.system.identity.entity.SysUserPosition;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.entity.UserRole;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
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
 * 统一权限策略服务。
 * <p>
 * 负责策略的 CRUD 以及用户策略的加载与合并。
 * 实现 {@link PolicyLoader} 供缓存层回源调用。
 */
@Slf4j
@Service
public class PolicyService implements PolicyLoader {

    /**
     * 开放平台应用主体的资源归属：平台内置应用。
     * <p>
     * 开放接口（{@code /open/**}）的资源都属于平台自身模块，故 APP 策略行的 {@code app_code}
     * 固定为平台内置应用；{@code subject_id} 才是被授权的开放平台应用编码。
     * <p>
     * 取值必须与 {@code ApiResourceRegistry.PLATFORM_APP_CODE} 一致；此处独立声明是为了
     * 保持 biz 层不依赖 web 层（分层约定 biz → dal → web）。
     */
    public static final String PLATFORM_APP_CODE = "nway-system";

    /** 主体类型白名单（fail-closed：未登记类型拒绝写入，防止拼写错误静默产出永不生效的策略） */
    private static final Set<String> SUBJECT_TYPES = Set.of("ROLE", "USER", "POSITION", "APP");

    /** 平台级跨应用角色标识：持有该 app_code 的角色不受"策略应用范围"约束 */
    private static final String WILDCARD_APP_CODE = "*";

    /** 资源通配符：表示该应用全部资源（不参与资源存在性校验） */
    private static final String WILDCARD_RESOURCE_CODE = "*";

    @Autowired
    private PermissionPolicyDao policyDao;
    @Autowired
    private AppDao appDao;
    @Autowired
    private MenuDao menuDao;
    @Autowired
    private ResourceDao resourceDao;
    @Autowired
    private UserRoleDao userRoleDao;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private SysUserPositionDao userPositionDao;
    @Autowired
    private SysPositionDao positionDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private PolicyMerger policyMerger;
    @Autowired
    @Lazy
    private PolicyCacheManager policyCacheManager;

    @Autowired
    private AuthzVersionService authzVersionService;

    @Autowired
    private GrantCacheService grantCacheService;

    @Autowired
    private GrantActionAdvisor grantActionAdvisor;

    // ====== PolicyLoader 实现 ======

    @Override
    public Map<String, MergedPolicy> loadFromDb(String employeeId) {
        if (StringUtils.isBlank(employeeId)) {
            return Collections.emptyMap();
        }

        // 1. USER 级策略
        List<PermissionPolicy> userPolicies = policyDao.queryByUser(employeeId);

        // 2. ROLE 级策略
        List<UserRole> userRoles = userRoleDao.queryByUser(List.of(employeeId));
        List<PermissionPolicy> rolePolicies = Collections.emptyList();
        if (!CollectionUtils.isEmpty(userRoles)) {
            List<String> roleIds = userRoles.stream()
                    .map(UserRole::getRoleId).distinct().collect(Collectors.toList());
            rolePolicies = policyDao.queryByRoles(roleIds);
        }

        // 3. POSITION 级策略
        List<SysUserPosition> userPositions = userPositionDao.queryByEmployee(employeeId);
        List<PermissionPolicy> positionPolicies = Collections.emptyList();
        if (!CollectionUtils.isEmpty(userPositions)) {
            List<String> positionIds = userPositions.stream()
                    .map(SysUserPosition::getPositionId).distinct().collect(Collectors.toList());
            positionPolicies = policyDao.queryByPositions(positionIds);
        }

        // 4. 合并
        return policyMerger.merge(userPolicies, rolePolicies, positionPolicies);
    }

    // ====== 策略 CRUD ======

    /**
     * 保存策略（id 为空新增，否则更新）。
     * <p>
     * {@code tenant_code} 由<b>主体</b>推导，不接受调用方传入。原因：非超管用户若能显式指定
     * tenantCode，就可以把策略写进 public 租户，而公共租户的策略因读隔离集合自动并入 public
     * 而对所有租户生效 → 等于自授权（审计 P0-2）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(PermissionPolicy policy) {
        validate(policy);
        SubjectScope scope = resolveSubject(policy.getSubjectType(), policy.getSubjectId());
        policy.setTenantCode(scope.tenantCode());
        assertRoleAppScope(scope, policy.getAppCode());
        assertResourceExists(policy.getAppCode(), policy.getResourceCode());

        if (StringUtils.isNotBlank(policy.getId())) {
            // 更新：原记录必须与主体同租户，防止把行改租户挪进 public，或改到别的租户名下
            PermissionPolicy existing = policyDao.getById(policy.getId());
            if (existing == null) {
                throw new NwaySystemException("策略不存在或无权限修改");
            }
            if (!scope.tenantCode().equals(existing.getTenantCode())) {
                throw new NwaySystemException("策略主体归属租户与原记录不一致，不能修改");
            }
        }

        TenantUtil.assertAdminCanManageTenant(scope.tenantCode());

        // 只告警不阻断：写类动作缺少 read 时提醒（避免“能改不能看”的误配）
        grantActionAdvisor.warnIfWriteWithoutRead(policy.getAppCode(), policy.getResourceCode(),
                policy.getActions());

        if (StringUtils.isBlank(policy.getId())) {
            policyDao.insert(policy);
        } else {
            policyDao.updateById(policy);
        }

        // 失效相关用户缓存
        evictCacheBySubject(policy.getSubjectType(), policy.getSubjectId());
        publishChange();
    }

    /**
     * 主体作用域：租户 + （仅 ROLE 主体）角色所属应用。
     *
     * @param tenantCode  主体所属租户
     * @param roleAppCode 角色所属应用；非 ROLE 主体或平台级角色为 null（不受应用限制）
     */
    private record SubjectScope(String tenantCode, String roleAppCode) {
    }

    /**
     * 解析主体的作用域：ROLE → 角色租户与应用；USER → 用户租户；POSITION → 岗位租户。
     * <p>
     * APP 主体不走本入口——开放平台应用策略由 {@link #saveAppPolicies} 统一强制为公共租户。
     */
    private SubjectScope resolveSubject(String subjectType, String subjectId) {
        SubjectScope scope = switch (subjectType) {
            case "ROLE" -> {
                Role role = roleDao.queryById(subjectId);
                if (role == null) {
                    throw new NwaySystemException("角色不存在：" + subjectId);
                }
                String appCode = WILDCARD_APP_CODE.equals(role.getAppCode()) ? null : role.getAppCode();
                yield new SubjectScope(role.getTenantCode(), appCode);
            }
            case "USER" -> {
                User user = userDao.getByEmployeeId(subjectId);
                if (user == null) {
                    throw new NwaySystemException("用户不存在：" + subjectId);
                }
                yield new SubjectScope(user.getTenantCode(), null);
            }
            case "POSITION" -> {
                SysPosition position = positionDao.queryById(subjectId);
                if (position == null) {
                    throw new NwaySystemException("岗位不存在：" + subjectId);
                }
                yield new SubjectScope(position.getTenantCode(), null);
            }
            default -> throw new NwaySystemException("主体类型 " + subjectType
                    + " 不支持单条保存；开放平台应用策略请使用 saveAppPolicies");
        };
        if (StringUtils.isBlank(scope.tenantCode())) {
            throw new NwaySystemException("主体缺少租户归属，无法确定策略租户：" + subjectType + ":" + subjectId);
        }
        return scope;
    }

    /**
     * 角色策略的应用范围：非平台级角色只能持有「角色所属租户可见」的应用资源策略。
     * <p>
     * 判定以<b>租户</b>为边界，而不是要求角色 app_code 与策略 app_code 完全相同：
     * <ul>
     *   <li>角色常按共享应用（如 wform）建立，而页面菜单发布在租户自己的低代码应用下；
     *       强制同应用会让「新表单上线后无法授权」，只能绕过界面直接调授权接口（C8）；</li>
     *   <li>平台内置应用（default）与共享应用（public）的资源对所有租户可授权，也须放行；</li>
     *   <li>授权运营台的 grants 写入口本就不限制角色 app_code，两处规则需保持一致。</li>
     * </ul>
     * 仍拒绝其他租户的私有应用：这类策略对本租户用户永远不可达，属误配而非跨应用授权。
     * 需要跨全部应用授权时仍可使用平台级角色（{@code app_code='*'}）。
     */
    private void assertRoleAppScope(SubjectScope scope, String appCode) {
        String roleAppCode = scope.roleAppCode();
        if (roleAppCode == null || roleAppCode.equals(appCode)) {
            return;
        }
        // 跨租户查询：应用本体可能记在 default（平台）或 public（共享），不能受调用方租户过滤影响
        App app = TenantUtil.callAsSystem(() -> appDao.getByCode(appCode));
        if (app == null) {
            throw new NwaySystemException("应用不存在：" + appCode);
        }
        boolean visibleToRoleTenant = scope.tenantCode().equals(app.getTenantCode())
                || TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode())
                || TenantUtil.DEFAULT_TENANT_CODE.equals(app.getTenantCode());
        if (!visibleToRoleTenant) {
            throw new NwaySystemException("应用 " + appCode + " 归属租户 " + app.getTenantCode()
                    + "，不在角色租户 " + scope.tenantCode() + " 的可授权范围内（本租户 / default / public）");
        }
    }

    /**
     * 资源存在性校验：策略的 {@code (app_code, resource_code)} 必须对应一个已登记的资源。
     * <p>
     * 资源有两种合法登记方式，满足其一即可：
     * <ul>
     *   <li>{@code sys_menu} 中的 PAGE 菜单（导航页面权限）；</li>
     *   <li>{@code sys_resource} 资源目录（API / CAPABILITY 等非菜单资源，如 AI 接口
     *       {@code ai:{interfaceCode}}、{@code kql:workItemList}）——这类策略直接参与
     *       决策判定，同样不能是孤儿。</li>
     * </ul>
     * 否则写入的策略永不生效（孤儿策略），表现为"配置成功但无权限"，极难排查；且资源码改名后
     * 存量策略会静默失效，此校验把这类误配挡在写入时。通配符 {@code '*'} 表示该应用全部资源，
     * 不参与校验。
     * <p>
     * 跨租户查询（{@link TenantUtil#callAsSystem}）：资源本体记在 default（平台应用）或
     * public（共享应用），而策略主体在业务租户，不能在调用方租户上下文下判定。
     */
    private void assertResourceExists(String appCode, String resourceCode) {
        if (WILDCARD_RESOURCE_CODE.equals(resourceCode)) {
            return;
        }
        boolean exists = TenantUtil.callAsSystem(() -> menuDao.existsPageResource(appCode, resourceCode)
                || resourceDao.exists(appCode, resourceCode));
        if (!exists) {
            throw new NwaySystemException("资源不存在：" + appCode + ":" + resourceCode
                    + "，请先在菜单管理或资源登记台登记（或使用通配符 '*'）");
        }
    }

    /**
     * 批量保存角色的策略（替换式：清空该角色旧策略后写入新策略）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRolePolicies(String roleId, List<PermissionPolicy> policies) {
        Role role = roleDao.queryById(roleId);
        if (role == null) {
            throw new NwaySystemException("角色不存在");
        }
        TenantUtil.assertAdminCanManageTenant(role.getTenantCode());

        // 清空该角色旧策略
        policyDao.deleteByRole(roleId);

        // 写入新策略
        if (!CollectionUtils.isEmpty(policies)) {
            SubjectScope scope = new SubjectScope(role.getTenantCode(),
                    WILDCARD_APP_CODE.equals(role.getAppCode()) ? null : role.getAppCode());
            for (PermissionPolicy p : policies) {
                assertRoleAppScope(scope, p.getAppCode());
                assertResourceExists(p.getAppCode(), p.getResourceCode());
                grantActionAdvisor.warnIfWriteWithoutRead(p.getAppCode(), p.getResourceCode(), p.getActions());
                p.setSubjectType("ROLE");
                p.setSubjectId(roleId);
                p.setTenantCode(role.getTenantCode());
            }
            policyDao.batchInsert(policies);
        }

        // 失效该角色下所有用户的缓存
        evictCacheByRole(roleId);
        publishChange();
    }

    /**
     * 删除策略
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        PermissionPolicy policy = policyDao.getById(id);
        if (policy == null) {
            return;
        }
        TenantUtil.assertAdminCanManageTenant(policy.getTenantCode());
        policyDao.deleteById(id);
        evictCacheBySubject(policy.getSubjectType(), policy.getSubjectId());
        publishChange();
    }

    /**
     * 删除角色的全部策略（角色删除时联动调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByRole(String roleId) {
        policyDao.deleteByRole(roleId);
        evictCacheByRole(roleId);
        publishChange();
    }

    /**
     * 幂等授予角色在某应用资源上的查看策略（低代码菜单发布后自动授权用）。
     * <p>
     * 该角色已存在同 appCode + resourceCode 的策略时跳过，保证重复发布不重复授权。
     * 数据范围取最保守的 SELF，仅用于满足菜单可见性推导（用户在资源上有任意 action 即可见），
     * 页面内数据权限应由管理员按需调整。
     *
     * @param role         目标角色（须已校验租户与应用归属）
     * @param appCode      资源所属应用
     * @param resourceCode 资源标识
     * @return true=本次实际写入策略
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean ensureRoleResourcePolicy(Role role, String appCode, String resourceCode) {
        assertRoleAppScope(new SubjectScope(role.getTenantCode(),
                WILDCARD_APP_CODE.equals(role.getAppCode()) ? null : role.getAppCode()), appCode);
        boolean exists = policyDao.queryByRole(role.getId()).stream()
                .anyMatch(p -> appCode.equals(p.getAppCode()) && resourceCode.equals(p.getResourceCode()));
        if (exists) {
            return false;
        }
        PermissionPolicy policy = new PermissionPolicy();
        policy.setSubjectType("ROLE");
        policy.setSubjectId(role.getId());
        policy.setAppCode(appCode);
        policy.setResourceCode(resourceCode);
        policy.setActions(JSON.toJSONString(List.of("view")));
        policy.setDataScope("SELF");
        policy.setTenantCode(role.getTenantCode());
        policyDao.insert(policy);
        evictCacheByRole(role.getId());
        publishChange();
        return true;
    }

    // ====== 查询 ======

    /**
     * 查询角色的全部策略（管理后台展示用）
     */
    public List<PermissionPolicy> queryByRole(String roleId) {
        return policyDao.queryByRole(roleId);
    }

    /**
     * 查询主体的全部策略
     */
    public List<PermissionPolicy> queryBySubject(String subjectType, String subjectId) {
        return policyDao.queryBySubject(subjectType, subjectId);
    }

    /**
     * 查询开放平台应用主体（APP）的策略。
     * <p>
     * APP 主体<b>不参与</b> {@link PolicyMerger} 的人维度合并（角色 / 岗位 / 用户）——
     * 开放平台的应用授权是独立令牌链路，直接展开即为该应用的全部策略
     * （见 权限模型重构技术方案 §4.1.3）。
     *
     * @param appCode 开放平台应用编码（subject_id）
     */
    public List<PermissionPolicy> queryByAppSubject(String appCode) {
        return policyDao.queryByAppSubject(appCode);
    }

    /**
     * 批量保存开放平台应用（APP）的策略（替换式：清空该应用旧策略后写入新策略）。
     * <p>
     * 服务端强制 {@code subjectType=APP}、{@code subjectId=appCode}，
     * 且资源归属固定为平台内置应用（{@link #PLATFORM_APP_CODE}），不接受调用方传入，
     * 避免越权把策略写到其他主体或其他应用的资源命名空间上。
     * <p>
     * {@code tenant_code} 固定为公共租户：开放平台应用授权是<b>平台级集成配置</b>，
     * 全局唯一一份——若交给「当前登录租户」自动填充，不同租户的管理员会各写一份，
     * 而替换式删除受租户隔离限制只清得掉自己那份，导致同一应用出现冲突的多份授权。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAppPolicies(String appCode, List<PermissionPolicy> policies) {
        if (StringUtils.isBlank(appCode)) {
            throw new NwaySystemException("应用编码不能为空");
        }
        // 写公共租户资源本体须超管或经 sys_admin_tenant 显式授权（读共享 ≠ 写授权）
        TenantUtil.assertAdminCanManageTenant(TenantUtil.PUBLIC_TENANT_CODE);
        policyDao.deleteBySubject("APP", appCode);
        if (CollectionUtils.isEmpty(policies)) {
            return;
        }
        for (PermissionPolicy p : policies) {
            assertResourceExists(PLATFORM_APP_CODE, p.getResourceCode());
            p.setId(null);
            p.setSubjectType("APP");
            p.setSubjectId(appCode);
            p.setAppCode(PLATFORM_APP_CODE);
            p.setTenantCode(TenantUtil.PUBLIC_TENANT_CODE);
        }
        policyDao.batchInsert(policies);
        publishChange();
    }

    /**
     * 获取用户合并后的策略（走缓存）
     */
    public Map<String, MergedPolicy> getMergedPolicies(String employeeId) {
        Map<String, MergedPolicy> result = policyCacheManager.get(employeeId);
        return result != null ? result : Collections.emptyMap();
    }

    /**
     * 获取用户在指定应用下的合并策略
     */
    public List<MergedPolicy> getMergedPoliciesByApp(String employeeId, String appCode) {
        Map<String, MergedPolicy> all = getMergedPolicies(employeeId);
        return all.values().stream()
                .filter(p -> appCode.equals(p.getAppCode()) || "*".equals(p.getAppCode()))
                .collect(Collectors.toList());
    }

    // ====== 缓存失效 ======

    /**
     * 授权变更后：递增全局版本（版本化缓存 key 立即换新）并清空决策缓存。
     */
    private void publishChange() {
        authzVersionService.bump();
        grantCacheService.invalidateAll();
    }

    private void evictCacheBySubject(String subjectType, String subjectId) {
        switch (subjectType) {
            case "ROLE" -> evictCacheByRole(subjectId);
            case "USER" -> policyCacheManager.evict(subjectId);
            case "POSITION" -> evictCacheByPosition(subjectId);
        }
    }

    private void evictCacheByRole(String roleId) {
        // 缓存 key 为全局（employee_id 全局唯一），受影响用户必须不受操作管理员租户上下文影响。
        // 否则跨租户授权（如管理员在 default 租户下为 OA 租户角色配置策略）时，
        // queryByRole 因租户过滤查不到目标租户的用户绑定，导致这些用户缓存不被驱逐而长期看到旧权限。
        usersOfRole(roleId).forEach(policyCacheManager::evict);
    }

    /**
     * 失效角色下所有用户的权限缓存（事务提交后）。
     * <p>
     * 用于「角色本身」变更（改名、改所属应用）等会间接改变权限解释的场景；
     * 用户与角色的绑定关系变更由 {@code UserRoleService} 直接失效对应用户。
     */
    public void evictRoleUsersAfterCommit(String roleId) {
        usersOfRole(roleId).forEach(policyCacheManager::evictAfterCommit);
    }

    private List<String> usersOfRole(String roleId) {
        List<UserRole> userRoles = TenantUtil.callAsSystem(() -> userRoleDao.queryByRole(List.of(roleId)));
        if (CollectionUtils.isEmpty(userRoles)) {
            return List.of();
        }
        return userRoles.stream().map(UserRole::getEmployeeId).distinct().toList();
    }

    private void evictCacheByPosition(String positionId) {
        List<SysUserPosition> userPositions =
                TenantUtil.callAsSystem(() -> userPositionDao.queryByPosition(positionId));
        if (!CollectionUtils.isEmpty(userPositions)) {
            userPositions.stream()
                    .map(SysUserPosition::getEmployeeId)
                    .distinct()
                    .forEach(policyCacheManager::evict);
        }
    }

    // ====== 校验 ======

    private void validate(PermissionPolicy policy) {
        if (StringUtils.isBlank(policy.getSubjectType())) {
            throw new NwaySystemException("主体类型不能为空");
        }
        // fail-closed：未知主体类型拒绝写入。否则拼写错误（如 "ROlE"）会静默写入一条
        // 任何查询都不会命中的策略，表现为"配置成功但不生效"，极难排查。
        if (!SUBJECT_TYPES.contains(policy.getSubjectType())) {
            throw new NwaySystemException("不支持的主体类型: " + policy.getSubjectType()
                    + "，允许值 " + SUBJECT_TYPES);
        }
        if (StringUtils.isBlank(policy.getSubjectId())) {
            throw new NwaySystemException("主体ID不能为空");
        }
        if (StringUtils.isBlank(policy.getAppCode())) {
            throw new NwaySystemException("应用标识不能为空");
        }
        if (StringUtils.isBlank(policy.getResourceCode())) {
            throw new NwaySystemException("资源标识不能为空");
        }
        if (StringUtils.isBlank(policy.getDataScope())) {
            policy.setDataScope("SELF");
        }
    }
}
