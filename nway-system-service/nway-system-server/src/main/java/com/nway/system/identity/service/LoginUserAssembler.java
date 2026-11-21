package com.nway.system.identity.service;

import com.nway.system.common.security.LoginUser;
import com.nway.system.tenant.dao.AdminGrantDao;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.dao.SysUserDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.identity.entity.SysUserDept;
import com.nway.system.identity.dao.SysPositionDao;
import com.nway.system.identity.dao.SysUserPositionDao;
import com.nway.system.identity.entity.SysPosition;
import com.nway.system.identity.entity.SysUserPosition;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.entity.UserRole;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LoginUser 装配器：按工号从 DB 组装完整登录用户信息（部门、角色、岗位、租户等）。
 * <p>
 * 仅依赖 DAO 层，供 UserService、PolicyService 等共用，
 * 避免服务间为复用该方法而产生循环依赖。
 *
 * @author nway
 */
@Component
public class LoginUserAssembler {

    @Autowired
    private UserDao userDao;
    @Autowired
    private TenantDao tenantDao;
    @Autowired
    private SysDeptDao sysDeptDao;
    @Autowired
    private SysUserDeptDao userDeptDao;
    @Autowired
    private UserRoleDao userRoleDao;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private SysPositionDao positionDao;
    @Autowired
    private SysUserPositionDao userPositionDao;

    @Autowired
    private AdminGrantDao adminGrantDao;

    /**
     * 构建登录用户信息，补齐部门、角色、岗位、租户、状态等字段。
     * <p>
     * 数据权限依赖此方法填充的 deptCodeList/roleList/mainDeptCode/tenantCode。
     *
     * @param employeeId 工号
     * @return LoginUser；用户不存在返回 null
     */
    public LoginUser getByEmployeeId(String employeeId) {
        User user = userDao.getByEmployeeId(employeeId);
        if (user == null) {
            return null;
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setEmployeeId(user.getEmployeeId());
        loginUser.setRealName(user.getRealName());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setStatus(user.getStatus());
        loginUser.setTenantCode(user.getTenantCode());
        loginUser.setSuperAdmin(Boolean.TRUE.equals(user.getIsSuperAdmin()));
        loginUser.setMainDeptId(user.getDeptId());

        // 填充平台管理员标志与授权租户范围（仅管理员查询授权表，避免普通用户多余开销）
        boolean isAdmin = Boolean.TRUE.equals(user.getIsAdmin());
        loginUser.setAdmin(isAdmin);
        if (isAdmin) {
            loginUser.setAdminTenantCodes(adminGrantDao.queryTenantCodes(employeeId));
        } else {
            loginUser.setAdminTenantCodes(Collections.emptyList());
        }

        // 填充租户名称
        if (StringUtils.isNotBlank(user.getTenantCode())) {
            Tenant tenant = tenantDao.getByCode(user.getTenantCode());
            if (tenant != null) {
                loginUser.setTenantName(tenant.getTenantName());
            }
        }

        // 填充主部门编码（数据权限前缀匹配用）
        if (StringUtils.isNotBlank(user.getDeptId())) {
            SysDept dept = sysDeptDao.queryById(user.getDeptId());
            if (dept != null) {
                loginUser.setMainDeptCode(dept.getDeptCode());
            }
        }

        // 填充所有任职部门编码（主 + 兼，数据权限按并集过滤）
        List<SysUserDept> userDepts = userDeptDao.queryByEmployee(employeeId);
        if (!CollectionUtils.isEmpty(userDepts)) {
            List<String> deptIds = userDepts.stream().map(SysUserDept::getDeptId).distinct().collect(Collectors.toList());
            List<SysDept> depts = sysDeptDao.queryByIds(deptIds);
            loginUser.setDeptCodeList(depts.stream().map(SysDept::getDeptCode).collect(Collectors.toList()));
        } else if (loginUser.getMainDeptCode() != null) {
            loginUser.setDeptCodeList(Collections.singletonList(loginUser.getMainDeptCode()));
        }

        // 填充角色编码列表
        List<UserRole> userRoles = userRoleDao.queryByUser(List.of(employeeId));
        if (!CollectionUtils.isEmpty(userRoles)) {
            List<String> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().collect(Collectors.toList());
            List<Role> roles = roleDao.queryByIds(roleIds);
            loginUser.setRoleList(roles.stream().map(Role::getRoleCode).collect(Collectors.toList()));
        }

        // 填充岗位编码列表
        List<SysUserPosition> userPositions = userPositionDao.queryByEmployee(employeeId);
        if (!CollectionUtils.isEmpty(userPositions)) {
            List<String> positionIds = userPositions.stream().map(SysUserPosition::getPositionId).distinct().collect(Collectors.toList());
            List<SysPosition> positions = positionDao.queryByIds(positionIds);
            loginUser.setPositionCodes(positions.stream().map(SysPosition::getPositionCode).collect(Collectors.toList()));
        }

        return loginUser;
    }
}
