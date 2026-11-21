package com.nway.system.identity.service;

import com.google.common.collect.Lists;
import com.nway.system.authorization.service.policy.PolicyCacheManager;
import com.nway.system.identity.web.model.RoleUserDto;
import com.nway.system.identity.web.model.RoleUserSaveDto;
import com.nway.system.identity.web.model.UserRoleDto;
import com.nway.system.identity.web.model.UserRoleSaveDto;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.entity.UserRole;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserRoleService {

    @Autowired
    private UserRoleDao userRoleDao;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private PolicyCacheManager policyCacheManager;

    /**
     * 保存用户角色关系（按管理员可管理租户范围分区操作）。
     * <p>
     * 管理员只能操作其授权租户范围内的角色绑定，不影响其他租户的角色关联。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(UserRoleSaveDto userRoleSaveDto) {
        String employeeId = userRoleSaveDto.getEmployeeId();
        if (StringUtils.isBlank(employeeId)) {
            throw new NwaySystemException("工号不能为空");
        }

        // 校验目标用户在管理员可管理租户范围内
        User targetUser = userDao.getByEmployeeId(employeeId);
        if (targetUser == null) {
            throw new NwaySystemException("用户不存在");
        }
        TenantUtil.assertAdminCanManageTenant(targetUser.getTenantCode());

        // 校验所有目标角色在管理员可管理租户范围内
        List<String> newRoleIds = userRoleSaveDto.getRoleIds();
        if (!CollectionUtils.isEmpty(newRoleIds)) {
            List<Role> roles = roleDao.queryByIds(newRoleIds);
            if (roles.size() != newRoleIds.size()) {
                throw new NwaySystemException("存在不存在的角色");
            }
            for (Role role : roles) {
                // public 共享角色允许任意租户用户绑定（引用行为）；其余角色仍须持有对应租户管理权
                TenantUtil.assertAdminCanReferenceTenantResource(role.getTenantCode());
                // 同租户约束：public 角色可绑定任意租户用户，其余角色仍须与用户同租户
                if (!TenantUtil.PUBLIC_TENANT_CODE.equals(role.getTenantCode())
                        && !Objects.equals(role.getTenantCode(), targetUser.getTenantCode())) {
                    throw new NwaySystemException("角色 " + role.getRoleName() + " 与用户不属于同一租户，不能绑定");
                }
            }
        }

        // 分区解绑：仅删除管理员有权管理的角色绑定，保留不可管理的角色不动
        List<UserRole> existingRoles = userRoleDao.queryByUser(List.of(employeeId));
        Set<String> managedRoleIds = getManagedRoleIds(existingRoles);
        for (UserRole ur : existingRoles) {
            if (managedRoleIds.contains(ur.getRoleId())) {
                userRoleDao.deleteById(ur.getId());
            }
        }

        // 新增本次指定的角色绑定
        if (!CollectionUtils.isEmpty(newRoleIds)) {
            // 构建角色id→tenantCode映射，用于填充冗余字段
            Map<String, String> roleIdTenantMap = roleDao.queryByIds(newRoleIds).stream()
                    .collect(Collectors.toMap(Role::getId, r -> r.getTenantCode() != null ? r.getTenantCode() : ""));
            List<UserRole> userRoleList = new ArrayList<>();
            String operator = currentOperatorId();
            LocalDateTime now = LocalDateTime.now();
            for (String roleId : newRoleIds) {
                UserRole userRole = new UserRole();
                userRole.setEmployeeId(employeeId);
                userRole.setRoleId(roleId);
                userRole.setTenantCode(roleIdTenantMap.get(roleId));
                fillAudit(userRole, operator, now);
                userRoleList.add(userRole);
            }
            userRoleDao.insertBatch(userRoleList);
        }
        // 绑定关系变更后失效该用户的合并策略缓存（事务提交后），否则最长 10 分钟仍按旧角色判定
        policyCacheManager.evictAfterCommit(employeeId);
    }

    /**
     * 按角色批量覆盖其下用户（角色列表"用户管理"入口）。
     * <p>
     * 管理员仅可操作其授权租户范围内的角色和用户。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveByRole(RoleUserSaveDto dto) {
        if (StringUtils.isBlank(dto.getRoleId())) {
            throw new NwaySystemException("角色不能为空");
        }

        // 校验角色在管理员可管理租户范围内（public 共享角色为引用行为，放行）
        Role role = roleDao.queryById(dto.getRoleId());
        if (role == null) {
            throw new NwaySystemException("角色不存在");
        }
        TenantUtil.assertAdminCanReferenceTenantResource(role.getTenantCode());

        // 校验所有目标用户在管理员可管理租户范围内且与角色同租户（public 角色可绑定任意租户用户）
        if (!CollectionUtils.isEmpty(dto.getEmployeeIds())) {
            List<User> users = userDao.getByEmpids(dto.getEmployeeIds());
            for (User user : users) {
                TenantUtil.assertAdminCanManageTenant(user.getTenantCode());
                if (!TenantUtil.PUBLIC_TENANT_CODE.equals(role.getTenantCode())
                        && !Objects.equals(user.getTenantCode(), role.getTenantCode())) {
                    throw new NwaySystemException("用户 " + user.getRealName() + " 与角色不属于同一租户，不能绑定");
                }
            }
        }

        // 全量替换该角色下的用户（角色维度操作，不涉及跨租户问题）
        // 先记录变更前用户，保证“被移除”的用户也会失效缓存
        Set<String> affected = new HashSet<>();
        for (UserRole ur : userRoleDao.queryByRole(List.of(dto.getRoleId()))) {
            affected.add(ur.getEmployeeId());
        }
        userRoleDao.deleteByRole(dto.getRoleId());
        if (CollectionUtils.isEmpty(dto.getEmployeeIds())) {
            affected.forEach(policyCacheManager::evictAfterCommit);
            return;
        }
        List<UserRole> userRoleList = new ArrayList<>();
        String operator = currentOperatorId();
        LocalDateTime now = LocalDateTime.now();
        for (String employeeId : dto.getEmployeeIds()) {
            UserRole userRole = new UserRole();
            userRole.setRoleId(dto.getRoleId());
            userRole.setEmployeeId(employeeId);
            userRole.setTenantCode(role.getTenantCode());
            fillAudit(userRole, operator, now);
            userRoleList.add(userRole);
            affected.add(employeeId);
        }
        userRoleDao.insertBatch(userRoleList);
        affected.forEach(policyCacheManager::evictAfterCommit);
    }

    /**
     * 绑定关系写入审计字段（D15）：批量插入不会自动填充 creator_id/create_time，
     * 导致审计追踪缺失。操作人取当前登录工号（与 sys_user_role.creator_id 的列语义一致）。
     */
    private static void fillAudit(UserRole userRole, String operator, LocalDateTime now) {
        userRole.setCreatorId(operator);
        userRole.setCreateTime(now);
    }

    /** 当前操作人工号；无登录上下文（初始化/迁移脚本）时返回 null。 */
    private static String currentOperatorId() {
        var loginUser = LoginUserContext.current();
        return loginUser != null ? loginUser.getEmployeeId() : null;
    }

    /**
     * 获取管理员可管理的角色 ID 集合（用于分区解绑）。
     * <p>
     * 超管/无上下文：全部可管理。
     * 管理员：仅授权租户内的角色可管理。
     * 普通用户（不应走到这里，但防御性处理）：不可管理。
     */
    private Set<String> getManagedRoleIds(List<UserRole> existingRoles) {
        if (CollectionUtils.isEmpty(existingRoles)) {
            return Collections.emptySet();
        }
        if (TenantUtil.isSuperAdmin()) {
            return existingRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        }
        List<String> adminTenants = TenantUtil.currentAdminTenantCodes();
        if (adminTenants.isEmpty() && !TenantUtil.isAdmin()) {
            // 普通用户：全部可管理（其菜单权限已控制是否能调用此接口）
            return existingRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        }
        // 管理员：按角色所属租户过滤（public 共享角色的绑定关系视为可管理，否则分区保存时无法解除）
        Set<String> roleIds = existingRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        List<Role> roles = roleDao.queryByIds(roleIds);
        return roles.stream()
                .filter(role -> adminTenants.contains(role.getTenantCode())
                        || TenantUtil.PUBLIC_TENANT_CODE.equals(role.getTenantCode()))
                .map(Role::getId)
                .collect(Collectors.toSet());
    }

    public List<UserRoleDto> queryByUser(String userId) {
        List<UserRoleDto> userRoleDtos = queryByUser(Lists.newArrayList(userId)).getOrDefault(userId, List.of());
        userRoleDtos.forEach(userRoleDto -> userRoleDto.setUserId(null));
        return userRoleDtos;
    }

    public Map<String, List<UserRoleDto>> queryByUser(List<String> userIds) {
        List<UserRole> userRoles = userRoleDao.queryByUser(userIds);
        if (CollectionUtils.isEmpty(userRoles)) {
            return Map.of();
        }
        Set<String> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        List<Role> roles = roleDao.queryByIds(roleIds);
        Map<String, Role> roleMap = roles.stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        return userRoles.stream().map(userRole -> {
            Role role = roleMap.get(userRole.getRoleId());
            if (role == null) {
                return null;
            }
            UserRoleDto userRoleDto = new UserRoleDto();
            userRoleDto.setUserId(userRole.getEmployeeId());
            userRoleDto.setRoleId(role.getId());
            userRoleDto.setRoleCode(role.getRoleCode());
            userRoleDto.setRoleName(role.getRoleName());
            return userRoleDto;
        }).filter(Objects::nonNull).collect(Collectors.groupingBy(UserRoleDto::getUserId));
    }

    public List<RoleUserDto> queryByRole(String roleId) {
        return queryByRole(Lists.newArrayList(roleId)).getOrDefault(roleId, List.of());
    }

    public Map<String, List<RoleUserDto>> queryByRole(List<String> roleIds) {
        List<UserRole> userRoles = userRoleDao.queryByRole(roleIds);
        if (CollectionUtils.isEmpty(userRoles)) {
            return Map.of();
        }
        Set<String> empIds = userRoles.stream().map(UserRole::getEmployeeId).collect(Collectors.toSet());
        List<User> userList = userDao.getByEmpids(empIds);
        Map<String, User> userMap = userList.stream().collect(Collectors.toMap(User::getEmployeeId, Function.identity()));
        return userRoles.stream().map(userRole -> {
            User user = userMap.get(userRole.getEmployeeId());
            if (user == null) {
                return null;
            }
            RoleUserDto roleUserDto = new RoleUserDto();
            roleUserDto.setRoleId(userRole.getRoleId());
            roleUserDto.setEmployeeId(userRole.getEmployeeId());
            roleUserDto.setUserName(user.getRealName());
            return roleUserDto;
        }).filter(Objects::nonNull).collect(Collectors.groupingBy(RoleUserDto::getRoleId));
    }

}
