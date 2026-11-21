package com.nway.system.identity.service;

import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.tenant.domain.TenantCodePolicy;
import com.nway.system.authorization.service.policy.PolicyCacheManager;
import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.identity.web.model.UserDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.authorization.domain.MergedPolicy;
import com.nway.system.authorization.service.decision.AuthzDecisionService;
import com.nway.system.authorization.service.decision.SubjectContext;
import com.nway.system.authorization.service.decision.SubjectContextResolver;
import com.nway.system.common.web.PageVo;
import com.nway.system.identity.domain.enums.UserStatusEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.identity.event.UserChangedEvent;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.identity.support.TokenInvalidator;
import com.nway.system.identity.service.TokenVersionPubSubService;
import com.nway.system.identity.service.UserStatusCache;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.dao.SysUserDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.identity.entity.SysUserDept;
import com.nway.system.identity.dao.SysUserPositionDao;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.entity.UserRole;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.web.model.UserQuery;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.util.SensitiveDataUtil;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.dao.UserLoginKeyDao;
import com.nway.system.identity.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private UserDao userDao;
    @Autowired
    private UserLoginKeyDao loginKeyDao;
    @Autowired
    private UserLoginKeyService loginKeyService;
    @Autowired
    private TokenInvalidator tokenInvalidator;
    @Autowired
    private SysDeptDao sysDeptDao;
    @Autowired
    private SysUserDeptDao userDeptDao;
    @Autowired
    private TenantDao tenantDao;
    @Autowired
    private UserStatusCache userStatusCache;
    @Autowired
    private TokenVersionPubSubService tokenVersionPubSubService;
    @Autowired
    private PolicyService policyService;
    @Autowired
    private PolicyCacheManager policyCacheManager;
    @Autowired
    private UserRoleDao userRoleDao;
    @Autowired
    private SysUserPositionDao userPositionDao;
    @Autowired
    private AuthzDecisionService authzDecisionService;
    @Autowired
    private SubjectContextResolver subjectContextResolver;
    @Autowired
    private LoginUserAssembler loginUserAssembler;
    @Autowired
    private com.nway.system.identity.dao.RoleDao roleDao;

    public PageVo<UserDto> queryPage(UserQuery query) {
        applyUserDataPermission(query);
        com.nway.spring.jdbc.pagination.Page<User> userPage = userDao.queryPage(query);
        PageVo<UserDto> pageVo = ConverterUtil.convert(userPage, UserDto.class);
        // 填充租户名称与部门名称
        fillTenantNames(pageVo.getData());
        fillDeptNames(pageVo.getData());
        // 敏感字段脱敏（仅列表展示场景，不影响入库与编辑回显）；超管可见完整信息（任命管理员等场景）
        if (!TenantUtil.isSuperAdmin()) {
            pageVo.getData().forEach(this::maskSensitive);
        }
        return pageVo;
    }

    /**
     * 对 UserDto 中的手机号、身份证号、邮箱、姓名进行脱敏。
     * <p>仅在查询输出（如列表展示）时调用，不可用于接收写入参数的 UserDto。</p>
     */
    private void maskSensitive(UserDto dto) {
        if (dto == null) {
            return;
        }
        dto.setPhoneNumber(SensitiveDataUtil.maskPhone(dto.getPhoneNumber()));
        dto.setIdNumber(SensitiveDataUtil.maskIdCard(dto.getIdNumber()));
        dto.setEmail(SensitiveDataUtil.maskEmail(dto.getEmail()));
        dto.setRealName(SensitiveDataUtil.maskName(dto.getRealName()));
    }

    /**
     * 平台用户列表数据权限接入（bizCode=nway-system, resourceCode=user）。
     * <p>
     * S2 起直接消费决策内核下发的数据谓词（{@code DataPredicate}），不再自行读策略字段：
     * 超管不限制；无权限或谓词为空 → 仅本人（fail-closed）；多谓词取最宽一类。
     */
    private void applyUserDataPermission(UserQuery query) {
        LoginUser user = LoginUserContext.current();
        if (user == null || user.isSuperAdmin()) {
            return;
        }
        SubjectContext subject = subjectContextResolver.from(user);
        AuthzDecisionService.Decision decision = authzDecisionService.decide(
                subject, "nway-system", "user", "view");
        if (!decision.allow()) {
            query.setCreatorId(user.getEmployeeId());
            return;
        }
        List<AuthzDecisionService.DataPredicate> predicates = decision.dataPredicates();
        if (predicates.stream().anyMatch(p -> com.nway.system.authorization.entity.SysGrant.SCOPE_ALL.equals(p.kind()))) {
            return;
        }
        AuthzDecisionService.DataPredicate deptTree = firstOf(predicates,
                com.nway.system.authorization.entity.SysGrant.SCOPE_DEPT_TREE);
        if (deptTree != null) {
            List<SysDept> depts = deptTree.values().stream()
                    .flatMap(code -> sysDeptDao.querySubTree(code).stream())
                    .toList();
            applyDeptIds(query, depts);
            return;
        }
        AuthzDecisionService.DataPredicate dept = firstOf(predicates,
                com.nway.system.authorization.entity.SysGrant.SCOPE_DEPT,
                com.nway.system.authorization.entity.SysGrant.SCOPE_DEPT_LIST);
        if (dept != null) {
            applyDeptIds(query, sysDeptDao.queryByCodes(dept.values()));
            return;
        }
        AuthzDecisionService.DataPredicate self = firstOf(predicates,
                com.nway.system.authorization.entity.SysGrant.SCOPE_SELF);
        if (self != null) {
            query.setCreatorId(user.getEmployeeId());
            return;
        }
        // 无任何可用谓词（NONE）：安全默认仅本人
        query.setCreatorId(user.getEmployeeId());
    }

    private static AuthzDecisionService.DataPredicate firstOf(
            List<AuthzDecisionService.DataPredicate> predicates, String... kinds) {
        for (String kind : kinds) {
            for (AuthzDecisionService.DataPredicate predicate : predicates) {
                if (kind.equals(predicate.kind())) {
                    return predicate;
                }
            }
        }
        return null;
    }

    private static void applyDeptIds(UserQuery query, List<SysDept> depts) {
        List<String> ids = depts.stream().map(SysDept::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            // 范围解析不出部门：绝不得退化为“不过滤”
            query.setCreatorId("__NONE__");
            return;
        }
        query.setDeptIdIn(ids);
    }

    public UserDto getById(String id) {
        User user = userDao.getById(id);
        // 租户隔离会把他租户/不存在的行过滤成 null；直接转换会抛 NPE 变成 500
        return user == null ? null : ConverterUtil.convert(user, UserDto.class);
    }

    /**
     * 保存用户（id 为空新增，否则更新）。
     *
     * @param userDto 用户信息
     * @return 新增用户时返回一次性初始密码；更新时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public String save(UserDto userDto) {
        if (StringUtils.isBlank(userDto.getId())) {
            return insert(userDto);
        }
        update(userDto);
        return null;
    }

    private String insert(UserDto userDto) {
        // 归属租户由服务端裁定（客户端不可指定越权租户），并强制契约编码规则
        String targetTenant = com.nway.system.tenant.domain.TenantCodePolicy.requireValid(
                resolveInsertTenant(userDto.getTenantCode()));
        // 防越权提权：superAdmin 标志与租户编码不接受客户端传值
        User user = ConverterUtil.convert(userDto, User.class, "isSuperAdmin", "tenantCode", "status");
        user.setTenantCode(targetTenant);
        user.setIsSuperAdmin(Boolean.FALSE);
        user.setStatus(UserStatusEnum.USING.getValue());
        userDao.insert(user);
        // 初始化登录信息，返回一次性初始密码（仅本次响应可见，首次登录须改密）
        String initialPassword = loginKeyService.initLoginKey(user);
        // 同步 sys_user_dept 主部门记录（保证 User.deptId 与 sys_user_dept 一致）
        if (StringUtils.isNotBlank(user.getDeptId())) {
            SysUserDept primary = new SysUserDept();
            primary.setEmployeeId(user.getEmployeeId());
            primary.setDeptId(user.getDeptId());
            primary.setIsPrimary(true);
            userDeptDao.insert(primary);
        }
        // 发布用户创建事件
        eventPublisher.publishEvent(new UserChangedEvent(this, "created",
                Map.of("employeeId", user.getEmployeeId(), "realName", user.getRealName())));
        return initialPassword;
    }

    /**
     * 裁定新建用户的归属租户。
     * <p>
     * 超管可指定任意租户；其余用户只能在本租户内创建，未归属租户时拒绝。
     */
    private String resolveInsertTenant(String requestedTenant) {
        LoginUser current = LoginUserContext.current();
        if (current == null) {
            throw new NwaySystemException("未登录，无法创建用户");
        }
        if (current.isSuperAdmin()) {
            if (StringUtils.isNotBlank(requestedTenant)) {
                return requestedTenant;
            }
            return StringUtils.isNotBlank(current.getTenantCode())
                    ? current.getTenantCode() : TenantUtil.DEFAULT_TENANT_CODE;
        }
        if (StringUtils.isBlank(current.getTenantCode())) {
            throw new NwaySystemException("当前用户未归属租户，无法创建用户");
        }
        // 非超管：强制落库到自身租户（管理员另需授权校验）
        TenantUtil.assertAdminCanManageTenant(current.getTenantCode());
        return current.getTenantCode();
    }

    private void update(UserDto userDto) {
        User target = userDao.getById(userDto.getId());
        if (target == null) {
            throw new NwaySystemException("用户不存在");
        }
        // 管理员只能操作授权租户范围内的用户
        assertCanManageUser(target);
        // 归属租户：仅当调用方对目标租户有管理权且确有变更时才允许迁移，
        // 否则维持“编辑接口忽略 tenantCode”的历史行为，防止越权搬迁
        String requestedTenant = userDto.getTenantCode();
        boolean tenantChanging = StringUtils.isNotBlank(requestedTenant)
                && !Strings.CS.equals(requestedTenant, target.getTenantCode());
        if (tenantChanging) {
            assertCanChangeTenantTo(requestedTenant);
        }
        // 防越权：工号、超管标志、状态不允许通过编辑接口直接改写；
        // tenantCode 单独按上面的裁定处理（变更时不在此处直接写库，交由迁移流程）
        User user = ConverterUtil.convert(userDto, User.class,
                "employeeId", "tenantCode", "isSuperAdmin", "status");
        if (tenantChanging) {
            // 迁至新租户后，原部门必然不属于新租户，主部门快捷字段一并清空
            user.setDeptId("");
            user.setTenantCode(requestedTenant);
        }
        userDao.updateById(user);
        if (tenantChanging) {
            migrateUserTenant(target, requestedTenant);
        }
        // 状态变更走独立流程（含登录键失效/Token 撤销等安全动作）
        if (UserStatusEnum.DISABLED.getValue().equals(userDto.getStatus())
                && !UserStatusEnum.DISABLED.getValue().equals(target.getStatus())) {
            disableUser(target.getEmployeeId());
        } else if (UserStatusEnum.USING.getValue().equals(userDto.getStatus())
                && UserStatusEnum.DISABLED.getValue().equals(target.getStatus())) {
            enableUser(target.getEmployeeId());
        }
        // 发布用户更新事件
        eventPublisher.publishEvent(new UserChangedEvent(this, "updated",
                Map.of("employeeId", target.getEmployeeId())));
    }

    /**
     * 裁定当前调用方是否有权将用户迁移至目标租户。
     * <p>
     * 目标租户必须真实存在；超管可迁至任意租户，其余调用方（含平台管理员）
     * 仅能迁至自身可管理租户集合内（普通用户集合仅含本租户，故无法跨租户搬迁）。
     */
    private void assertCanChangeTenantTo(String targetTenant) {
        if (tenantDao.getByCode(targetTenant) == null) {
            throw new NwaySystemException("目标租户不存在：" + targetTenant);
        }
        List<String> manageable = TenantUtil.currentManageableTenantCodes();
        if (manageable != null && !manageable.contains(targetTenant)) {
            throw new NwaySystemException("无权将用户迁移至租户：" + targetTenant);
        }
    }

    /**
     * 用户跨租户迁移的联动清理（与主更新同事务）。
     * <p>
     * 部门、岗位、角色均按租户隔离，用户迁至新租户后，指向旧租户的这些绑定成为
     * 跨租户不一致数据（模型约束：角色/部门须与用户同租户），必须一并处理：
     * <ol>
     *   <li>清空部门任职（sys_user_dept）</li>
     *   <li>清空岗位任职（sys_user_position）</li>
     *   <li>解绑与用户新租户不一致的角色（public 共享角色保留）</li>
     *   <li>登录凭证（sys_user_login_key）的租户编码同步迁至新租户</li>
     *   <li>强制重新登录：使携带旧租户的已签发 Token 失效，下次登录按新租户重建上下文</li>
     * </ol>
     */
    private void migrateUserTenant(User target, String newTenant) {
        String employeeId = target.getEmployeeId();
        // 1. 清空部门任职（部门归属旧租户）
        userDeptDao.deleteByEmployee(employeeId);
        // 2. 清空岗位任职（岗位归属旧租户）
        userPositionDao.deleteByEmployee(employeeId);
        // 3. 登录凭证随用户迁移租户，否则用户改密等按当前租户过滤的更新会命中 0 行
        loginKeyDao.updateTenantCodeByEmployeeId(employeeId, newTenant);
        // 4. 解绑与用户新租户不一致的角色（public 共享角色保留）
        List<UserRole> userRoles = userRoleDao.queryByUser(List.of(employeeId));
        if (!CollectionUtils.isEmpty(userRoles)) {
            List<String> roleIds = userRoles.stream().map(UserRole::getRoleId)
                    .distinct().collect(Collectors.toList());
            Map<String, Role> roleMap = roleDao.queryByIds(roleIds).stream()
                    .collect(Collectors.toMap(Role::getId, r -> r, (a, b) -> a));
            for (UserRole userRole : userRoles) {
                Role role = roleMap.get(userRole.getRoleId());
                String roleTenant = role != null ? role.getTenantCode() : userRole.getTenantCode();
                boolean keep = TenantUtil.PUBLIC_TENANT_CODE.equals(roleTenant)
                        || Strings.CS.equals(roleTenant, newTenant);
                if (!keep) {
                    userRoleDao.deleteById(userRole.getId());
                }
            }
        }
        // 5. 强制用户重新登录：自增 token 版本号使旧 Token 失效，并清理权限/状态缓存
        userDao.incrementTokenVersion(employeeId);
        userStatusCache.evictTokenVersion(employeeId);
        policyCacheManager.evict(employeeId);
        tokenVersionPubSubService.publishVersionChange(employeeId, TokenVersionPubSubService.ACTION_LOGOUT);
        log.warn("用户 {} 迁移租户 {} -> {}，已清理部门/岗位/跨租户角色绑定并强制重新登录",
                employeeId, target.getTenantCode(), newTenant);
    }

    /**
     * 禁用用户：状态置为 disabled + 登录键失效 + 清除所有 token + 失效权限缓存 + 失效用户状态缓存
     * <p>
     * 安全限制：超级管理员不可被禁用；不可禁用自己。
     *
     * @param employeeId 工号
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(String employeeId) {
        // 安全守卫：校验目标用户
        User targetUser = userDao.getByEmployeeId(employeeId);
        if (targetUser == null) {
            throw new NwaySystemException("用户不存在");
        }
        if (Boolean.TRUE.equals(targetUser.getIsSuperAdmin())) {
            throw new NwaySystemException("超级管理员不可被禁用");
        }
        LoginUser current = LoginUserContext.current();
        if (current != null && employeeId.equals(current.getEmployeeId())) {
            throw new NwaySystemException("不能禁用自己");
        }
        // 管理员只能操作授权租户范围内的用户
        assertCanManageUser(targetUser);
        // 1. 更新用户状态
        userDao.updateStatusByEmployeeId(employeeId, UserStatusEnum.DISABLED.getValue());
        // 2. 登录键置失效，阻止后续登录
        loginKeyService.disableByEmployeeId(employeeId);
        // 3. 清除所有 token（含权限缓存）
        tokenInvalidator.invalidateUserTokens(employeeId);
        // 4. 标记禁用状态并广播（禁用是唯一会写 nway_user_status=disabled 的入口）
        userStatusCache.markDisabled(employeeId);
        tokenVersionPubSubService.publishVersionChange(employeeId, TokenVersionPubSubService.ACTION_DISABLE);
        // 发布用户禁用事件
        eventPublisher.publishEvent(new UserChangedEvent(this, "disabled",
                Map.of("employeeId", employeeId)));
    }

    /**
     * 启用用户：状态置为 using + 登录键恢复有效 + 清除禁用标记
     * <p>
     * 安全限制：管理员只能操作授权租户范围内的用户。
     *
     * @param employeeId 工号
     */
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(String employeeId) {
        User targetUser = userDao.getByEmployeeId(employeeId);
        if (targetUser == null) {
            throw new NwaySystemException("用户不存在");
        }
        assertCanManageUser(targetUser);
        userDao.updateStatusByEmployeeId(employeeId, UserStatusEnum.USING.getValue());
        loginKeyDao.enableByEmployeeId(employeeId);
        userStatusCache.clearDisabled(employeeId);
        // 广播启用通知，使其他实例立即清除本地禁用标记
        tokenVersionPubSubService.publishVersionChange(employeeId, TokenVersionPubSubService.ACTION_ENABLE);
    }

    /**
     * 管理员重置指定用户密码为一次性随机密码，重置后该用户需重新登录并立即改密。
     * <p>
     * 安全限制：不可重置超管密码（除非自己是超管）；只能操作可管理租户范围内的用户。
     *
     * @param employeeId 目标用户工号
     * @return 一次性初始密码（仅本次响应可见）
     */
    public String adminResetPassword(String employeeId) {
        User targetUser = userDao.getByEmployeeId(employeeId);
        if (targetUser == null) {
            throw new NwaySystemException("用户不存在");
        }
        // 非超管不可重置超管密码
        if (Boolean.TRUE.equals(targetUser.getIsSuperAdmin()) && !TenantUtil.isSuperAdmin()) {
            throw new NwaySystemException("无权重置超级管理员密码");
        }
        assertCanManageUser(targetUser);
        return loginKeyService.adminResetPassword(employeeId);
    }

    /**
     * 校验当前用户是否有权管理目标用户（租户范围守卫）。
     * <p>
     * 超管放行；其余用户（含平台管理员）目标用户租户必须落在可管理租户集合内
     * （可管理集合 = 本租户 + 授权租户，不含 public 读共享租户）。
     *
     * @param targetUser 目标用户
     */
    private void assertCanManageUser(User targetUser) {
        if (TenantUtil.isSuperAdmin()) {
            return;
        }
        List<String> tenants = TenantUtil.currentManageableTenantCodes();
        if (tenants == null) {
            return;
        }
        if (StringUtils.isBlank(targetUser.getTenantCode()) || !tenants.contains(targetUser.getTenantCode())) {
            throw new NwaySystemException("无权操作该用户");
        }
    }

    /**
     * 判断用户是否已被禁用（仅查状态字段，轻量级）。
     *
     * @param employeeId 工号
     * @return true 表示已禁用；用户不存在返回 false
     */
    public boolean isUserDisabled(String employeeId) {
        User user = userDao.getByEmployeeId(employeeId);
        return user != null && UserStatusEnum.DISABLED.getValue().equals(user.getStatus());
    }

    /**
     * 构建登录用户信息，补齐部门、角色、租户、状态等字段。
     * <p>
     * 数据权限依赖此方法填充的 deptCodeList/roleList/mainDeptCode/tenantCode。
     * 实际装配逻辑见 {@link LoginUserAssembler}。
     */
    public LoginUser getByEmployeeId(String employeeId) {
        return loginUserAssembler.getByEmployeeId(employeeId);
    }

    /**
     * 批量填充用户列表的部门名称
     */
    private void fillDeptNames(List<UserDto> users) {
        if (CollectionUtils.isEmpty(users)) {
            return;
        }
        List<String> deptIds = users.stream()
                .map(UserDto::getDeptId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (deptIds.isEmpty()) {
            return;
        }
        Map<String, String> deptNameMap = sysDeptDao.queryByIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
        users.forEach(u -> {
            if (StringUtils.isNotBlank(u.getDeptId())) {
                u.setDeptName(deptNameMap.getOrDefault(u.getDeptId(), ""));
            }
        });
    }

    /**
     * 批量填充用户列表的租户名称
     */
    private void fillTenantNames(List<UserDto> users) {
        if (CollectionUtils.isEmpty(users)) {
            return;
        }
        List<String> tenantCodes = users.stream()
                .map(UserDto::getTenantCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (tenantCodes.isEmpty()) {
            return;
        }
        // 逐个查询并缓存（租户数量通常较少）
        Map<String, String> tenantNameMap = tenantCodes.stream()
                .collect(Collectors.toMap(
                        code -> code,
                        code -> {
                            Tenant t = tenantDao.getByCode(code);
                            return t != null ? t.getTenantName() : "";
                        },
                        (a, b) -> a
                ));
        users.forEach(u -> {
            if (StringUtils.isNotBlank(u.getTenantCode())) {
                u.setTenantName(tenantNameMap.getOrDefault(u.getTenantCode(), ""));
            }
        });
    }

    /**
     * 查询可分配给指定角色的用户列表（按角色所属租户过滤）。
     * <p>
     * 用于角色-用户绑定时的用户候选列表，确保只返回与角色同租户的用户。
     *
     * @param roleId  角色 ID
     * @param keyword 搜索关键字（工号/姓名模糊匹配），可为空
     * @return 分页用户列表
     */
    public PageVo<UserDto> queryForRoleBinding(String roleId, String keyword) {
        com.nway.system.identity.entity.Role role = roleDao.queryById(roleId);
        if (role == null) {
            throw new NwaySystemException("角色不存在");
        }
        // 校验管理员有权操作该角色的租户（public 共享角色为引用行为，放行）
        TenantUtil.assertAdminCanReferenceTenantResource(role.getTenantCode());

        UserQuery query = new UserQuery();
        // public 共享角色：不按角色租户过滤候选用户，由自动租户隔离收敛范围（本租户 + public）
        if (!TenantUtil.PUBLIC_TENANT_CODE.equals(role.getTenantCode())) {
            query.setTenantCode(role.getTenantCode());
        }
        query.setKeyword(keyword);
        query.setCurrent(1);
        query.setSize(100);
        com.nway.spring.jdbc.pagination.Page<User> userPage = userDao.queryPage(query);
        PageVo<UserDto> pageVo = ConverterUtil.convert(userPage, UserDto.class);
        fillDeptNames(pageVo.getData());
        return pageVo;
    }

}
