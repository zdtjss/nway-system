package com.nway.system.identity.service;

import com.nway.system.portal.service.MenuService;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.authorization.service.resource.ResourceService;
import com.nway.system.identity.web.model.RoleDto;
import com.nway.system.common.web.PageVo;
import com.nway.system.identity.event.RoleChangedEvent;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.identity.web.model.RoleQuery;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.entity.Menu;
import com.nway.system.authorization.entity.PermissionPolicy;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.entity.UserRole;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import com.nway.system.authorization.web.model.ResourceVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 */
@Service
public class RoleService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private UserRoleDao userRoleDao;
    @Autowired
    private PolicyService policyService;
    @Autowired
    private AppDao appDao;
    @Autowired
    private TenantDao tenantDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private MenuService menuService;
    @Autowired
    private ResourceService resourceService;

    /**
     * 平台级跨应用角色标识
     */
    private static final String WILDCARD_APP_CODE = "*";

    /**
     * 保存角色。
     * <p>
     * 角色租户的确定规则（方案 B：共享应用 + 各租户自建角色）：
     * <ul>
     *   <li>私有应用：角色租户强制等于应用租户（权限作用域与数据作用域一致）</li>
     *   <li>public 共享应用（如 wform/kql）：各租户可为共享应用自建角色，租户由调用方决定，
     *       不再强制改写为应用租户——否则租户建不出共享应用的角色，只能借道本租户私有应用的角色，
     *       产生"角色 app_code 与策略 app_code 不一致"的跨应用授权</li>
     *   <li>平台级角色（app_code='*'）或无应用：租户取调用方传入值</li>
     * </ul>
     * 缺省租户只在"当前可写租户唯一"时自动取值，否则报错要求显式指定，避免静默落到错误租户。
     */
    public void save(RoleDto roleDTO) {
        // 兜底校验：Controller 的 @Valid 只覆盖 HTTP 入口，内部调用（复制、初始化、脚本）也必须守住
        if (StringUtils.isBlank(roleDTO.getRoleName())) {
            throw new NwaySystemException("角色名称不能为空");
        }
        if (StringUtils.isBlank(roleDTO.getRoleCode())) {
            throw new NwaySystemException("角色编码不能为空");
        }
        // 去除首尾空白，避免 "  " 绕过 @NotBlank 语义后以空白名落库
        roleDTO.setRoleName(roleDTO.getRoleName().trim());
        roleDTO.setRoleCode(roleDTO.getRoleCode().trim());
        String appCode = StringUtils.trimToNull(roleDTO.getAppCode());
        if (appCode != null && !WILDCARD_APP_CODE.equals(appCode)) {
            App app = appDao.getByCode(appCode);
            if (app == null) {
                // fail-closed：应用不存在时无法确定权限作用域，不能静默按调用方租户写入
                throw new NwaySystemException("应用不存在：" + appCode);
            }
            if (!TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode())) {
                roleDTO.setTenantCode(app.getTenantCode());
            }
        }
        if (StringUtils.isBlank(roleDTO.getId())) {
            if (StringUtils.isBlank(roleDTO.getTenantCode())) {
                roleDTO.setTenantCode(uniqueWritableTenant());
            }
            TenantUtil.assertAdminCanManageTenant(roleDTO.getTenantCode());
        }
        Role role = ConverterUtil.convert(roleDTO, Role.class);
        if (StringUtils.isBlank(role.getId())) {
            role.setIsDeleted(Boolean.FALSE);
            roleDao.insert(role);
            eventPublisher.publishEvent(new RoleChangedEvent(this, Map.of("roleId", role.getId(), "action", "created")));
        } else {
            // 更新：归属租户不可变更 —— 角色的策略行与用户绑定都带租户，改租户会让三者脱节，
            // 须走专门迁移（同 UserService 的用户跨租户迁移流程）
            Role existing = roleDao.queryById(role.getId());
            if (existing == null) {
                throw new NwaySystemException("角色不存在或无权限修改");
            }
            role.setTenantCode(existing.getTenantCode());
            TenantUtil.assertAdminCanManageTenant(existing.getTenantCode());
            roleDao.updateById(role);
            eventPublisher.publishEvent(new RoleChangedEvent(this, Map.of("roleId", role.getId(), "action", "updated")));
            // 角色所属应用等属性变化会改变策略解释（如跨应用校验），失效该角色下用户的策略缓存
            policyService.evictRoleUsersAfterCommit(role.getId());
        }
    }

    /**
     * 复制角色到目标租户（方案 B 新租户开通支撑）。
     * <p>
     * 共享应用（wform/kql）的服务菜单记 public、对所有租户可读，但某租户要「开通」这些功能，
     * 须在本租户自建角色并自配策略。逐个资源手配成本高，故提供「按模板角色克隆」：
     * 把源角色（同应用、同编码、同名称）连同其全部策略复制到目标租户，策略的 tenant_code 与
     * subject_id 重指到新角色。
     * <p>
     * 规则与 {@link #save} 一致：
     * <ul>
     *   <li>私有应用角色只能留在应用租户，不允许复制到其他租户（权限作用域须与数据作用域一致）</li>
     *   <li>public 共享应用角色 / 平台级角色（app_code='*'）可复制到任意目标租户</li>
     *   <li>目标租户下已有「同应用同编码」角色时拒绝（避免撞唯一约束 (role_code, app_code, tenant_code)）</li>
     * </ul>
     *
     * @param sourceRoleId     模板角色 id
     * @param targetTenantCode 目标租户编码
     * @return 新建的角色
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleDto copyToTenant(String sourceRoleId, String targetTenantCode) {
        if (StringUtils.isBlank(sourceRoleId)) {
            throw new NwaySystemException("源角色不能为空");
        }
        if (StringUtils.isBlank(targetTenantCode)) {
            throw new NwaySystemException("目标租户不能为空");
        }
        // 源角色读取受租户隔离约束：只能在当前可读租户集合内选取模板，避免任意租户互相窥探配置
        Role source = roleDao.queryById(sourceRoleId);
        if (source == null) {
            throw new NwaySystemException("源角色不存在或无权限读取");
        }
        if (targetTenantCode.equals(source.getTenantCode())) {
            throw new NwaySystemException("目标租户与源角色相同，无需复制");
        }

        String appCode = source.getAppCode();
        if (StringUtils.isNotBlank(appCode) && !WILDCARD_APP_CODE.equals(appCode)) {
            App app = appDao.getByCode(appCode);
            if (app == null) {
                throw new NwaySystemException("源角色的应用不存在：" + appCode);
            }
            if (!TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode())
                    && !app.getTenantCode().equals(targetTenantCode)) {
                throw new NwaySystemException("私有应用角色不能复制到其他租户（应用 " + appCode
                        + " 归属租户 " + app.getTenantCode() + "）");
            }
        }

        TenantUtil.assertAdminCanManageTenant(targetTenantCode);

        boolean duplicated = roleDao.queryByTenantCodes(List.of(targetTenantCode)).stream()
                .anyMatch(r -> sameAppCode(r.getAppCode(), appCode) && r.getRoleCode().equals(source.getRoleCode()));
        if (duplicated) {
            throw new NwaySystemException("目标租户下已存在同应用同编码的角色：" + source.getRoleCode());
        }

        Role copy = new Role();
        copy.setRoleCode(source.getRoleCode());
        copy.setRoleName(source.getRoleName());
        copy.setAppCode(appCode);
        copy.setDescription(source.getDescription());
        copy.setTenantCode(targetTenantCode);
        copy.setIsDeleted(Boolean.FALSE);
        roleDao.insert(copy);

        // 复制策略：原样克隆（含 actions/dataScope），由 saveRolePolicies 统一重指主体与租户并校验资源存在性；
        // 源租户私有应用（如低代码应用）的策略在目标租户不可达，跳过而不是整单失败
        List<PermissionPolicy> sourcePolicies = policyService.queryByRole(sourceRoleId);
        if (!sourcePolicies.isEmpty()) {
            List<PermissionPolicy> copies = sourcePolicies.stream()
                    .filter(p -> appVisibleToTenant(p.getAppCode(), targetTenantCode))
                    .map(p -> {
                        PermissionPolicy np = new PermissionPolicy();
                        np.setAppCode(p.getAppCode());
                        np.setResourceCode(p.getResourceCode());
                        np.setActions(p.getActions());
                        np.setDataScope(p.getDataScope());
                        np.setScopeDeptIds(p.getScopeDeptIds());
                        return np;
                    }).collect(Collectors.toList());
            policyService.saveRolePolicies(copy.getId(), copies);
        }

        eventPublisher.publishEvent(new RoleChangedEvent(this, Map.of("roleId", copy.getId(), "action", "created")));
        return ConverterUtil.convert(copy, RoleDto.class);
    }

    private static boolean sameAppCode(String a, String b) {
        return Objects.equals(StringUtils.trimToNull(a), StringUtils.trimToNull(b));
    }

    /**
     * 资源所属应用是否对目标租户可授权：目标租户私有应用、default 平台应用、public 共享应用。
     * <p>
     * 跨租户查询：应用本体可能记在其他租户，不能受调用方租户过滤影响；应用已删除时返回 false（策略不可达）。
     */
    private boolean appVisibleToTenant(String appCode, String tenantCode) {
        App app = TenantUtil.callAsSystem(() -> appDao.getByCode(appCode));
        if (app == null) {
            return false;
        }
        return tenantCode.equals(app.getTenantCode())
                || TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode())
                || TenantUtil.DEFAULT_TENANT_CODE.equals(app.getTenantCode());
    }

    /**
     * 角色租户缺省时的取值：仅当当前可写租户集合唯一时返回该租户。
     * <p>
     * 存在多个候选（管理员未指定工作租户且有多个授权租户）或超管（不限租户）时，
     * 无法推断意图，要求调用方显式指定。
     */
    private String uniqueWritableTenant() {
        List<String> manageable = TenantUtil.currentManageableTenantCodes();
        if (manageable != null && manageable.size() == 1) {
            return manageable.get(0);
        }
        throw new NwaySystemException("请显式指定角色归属租户（当前租户上下文无法唯一确定）");
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteById(String id) {
        Role role = roleDao.queryById(id);
        TenantUtil.assertAdminCanManageTenant(role != null ? role.getTenantCode() : null);
        List<UserRole> userRoles = userRoleDao.queryByRole(List.of(id));
        if (!userRoles.isEmpty()) {
            throw new NwaySystemException("角色下有" + userRoles.size() + "个关联用户，不能删除");
        }
        // 清理角色策略
        policyService.deleteByRole(id);
        roleDao.deleteById(id);
        eventPublisher.publishEvent(new RoleChangedEvent(this, Map.of("roleId", id, "action", "deleted")));
    }

    public PageVo<RoleDto> queryPage(RoleQuery roleQuery) {
        Page<Role> page = roleDao.queryPage(roleQuery);
        PageVo<RoleDto> dtoPage = ConverterUtil.convert(page, RoleDto.class);

        // 批量填充应用名称和租户名称
        Set<String> appCodes = dtoPage.getData().stream()
                .map(RoleDto::getAppCode).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Map<String, App> appMap = appDao.queryByCodes(appCodes);

        Set<String> tenantCodes = dtoPage.getData().stream()
                .map(dto -> displayTenant(dto.getTenantCode(), dto.getAppCode(), appMap))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        Map<String, String> tenantNameMap = tenantDao.queryByTenantCode(tenantCodes).stream()
                .collect(Collectors.toMap(Tenant::getTenantCode, Tenant::getTenantName, (a, b) -> a));

        dtoPage.getData().forEach(dto -> {
            App app = appMap.get(dto.getAppCode());
            if (app != null) {
                dto.setAppName(app.getAppName());
            }
            String tenantCode = displayTenant(dto.getTenantCode(), dto.getAppCode(), appMap);
            if (StringUtils.isNotBlank(tenantCode)) {
                dto.setTenantCode(tenantCode);
                dto.setTenantName(tenantNameMap.getOrDefault(tenantCode, ""));
            }
        });
        return dtoPage;
    }

    /**
     * 展示用租户回填：仅私有应用可按应用租户回填。
     * <p>
     * 共享应用（public，如 wform/kql）的角色租户由使用方指定，不得回填成 public，
     * 否则租户自己的角色会被误显示为公共租户。
     */
    private String displayTenant(String roleTenant, String appCode, Map<String, App> appMap) {
        if (StringUtils.isNotBlank(roleTenant)) {
            return roleTenant;
        }
        App app = appMap.get(appCode);
        if (app == null || TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode())) {
            return null;
        }
        return app.getTenantCode();
    }

    public RoleDto queryById(String id) {
        Role role = roleDao.queryById(id);
        // 租户隔离会把他租户/不存在的行过滤成 null；直接转换会抛 NPE 变成 500
        return role == null ? null : ConverterUtil.convert(role, RoleDto.class);
    }

    public List<RoleDto> queryById(List<String> ids) {
        return ConverterUtil.convert(roleDao.queryByIds(ids), RoleDto.class);
    }

    /**
     * 策略配置弹窗的授权候选菜单（归属 role 权限，租户管理员无需菜单管理权限）。
     * <p>
     * 目标租户必须落在调用方可读范围内（超管不限），避免借 appCode/tenantCode 枚举他租户菜单。
     *
     * @param appCode    应用编码
     * @param tenantCode 目标角色租户；为空时按全量查询（仅超管可达）
     */
    public List<Menu> queryGrantableMenus(String appCode, String tenantCode) {
        assertReadableTenant(tenantCode);
        return menuService.queryByApp(appCode, tenantCode);
    }

    /**
     * 策略配置弹窗的资源动作目录（归属 role 权限）。
     * <p>
     * 复用资源目录的可见性规则：平台/共享资源全部可见，租户私有资源仅归属租户可见。
     *
     * @param appCode 应用编码
     */
    public List<ResourceVo> queryGrantableResources(String appCode) {
        return resourceService.list(appCode, null);
    }

    private static void assertReadableTenant(String tenantCode) {
        if (StringUtils.isBlank(tenantCode)) {
            return;
        }
        List<String> readable = TenantUtil.currentTenantCodesForIsolation();
        if (readable != null && !readable.contains(tenantCode)) {
            throw new NwaySystemException("无权限读取该租户的菜单：" + tenantCode);
        }
    }

    /**
     * 查询可分配给指定用户的角色列表
     */
    public List<RoleDto> queryAvailableForUser(String employeeId) {
        User user = userDao.getByEmployeeId(employeeId);
        if (user == null || StringUtils.isBlank(user.getTenantCode())) {
            return List.of();
        }
        TenantUtil.assertAdminCanManageTenant(user.getTenantCode());
        // 可分配角色 = 本租户角色 + public 共享角色
        List<Role> roles = roleDao.queryByTenantCodes(
                List.of(user.getTenantCode(), TenantUtil.PUBLIC_TENANT_CODE));
        return ConverterUtil.convert(roles, RoleDto.class);
    }
}
