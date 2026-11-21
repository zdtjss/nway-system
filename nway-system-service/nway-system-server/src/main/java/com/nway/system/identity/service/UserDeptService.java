package com.nway.system.identity.service;

import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.dao.SysUserDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.identity.entity.SysUserDept;
import com.nway.system.identity.dao.SysPositionDao;
import com.nway.system.identity.dao.SysUserPositionDao;
import com.nway.system.identity.entity.SysPosition;
import com.nway.system.identity.entity.SysUserPosition;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import com.nway.system.identity.web.model.UserDeptVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户部门关系服务（跨部门任职）。
 * <p>
 * 保留 sys_user.dept_id 作为主部门快捷字段，同时维护 sys_user_dept 多部门关系。
 */
@Service
@RequiredArgsConstructor
public class UserDeptService {

    private final SysUserDeptDao userDeptDao;
    private final SysDeptDao sysDeptDao;
    private final UserDao userDao;
    private final SysUserPositionDao userPositionDao;
    private final SysPositionDao positionDao;

    /**
     * 查询用户所有任职部门
     */
    public List<SysUserDept> queryByEmployee(String employeeId) {
        return userDeptDao.queryByEmployee(employeeId);
    }

    /**
     * 查询部门下所有任职用户（含兼职）
     */
    public List<SysUserDept> queryByDept(String deptId) {
        return userDeptDao.queryByDept(deptId);
    }

    /**
     * 查询部门下所有任职用户（含兼职），返回用户信息与任职部门列表
     */
    public List<UserDeptVo> queryUsersByDept(String deptId) {
        List<SysUserDept> rels = userDeptDao.queryByDept(deptId);
        if (CollectionUtils.isEmpty(rels)) {
            return List.of();
        }
        Set<String> empIds = rels.stream()
                .map(SysUserDept::getEmployeeId)
                .collect(Collectors.toSet());
        List<User> users = userDao.getByEmpids(empIds);
        if (CollectionUtils.isEmpty(users)) {
            return List.of();
        }
        // 批量查询这些用户的全部任职部门（含主部门标记）
        List<SysUserDept> allRels = userDeptDao.queryByEmployees(empIds);
        Map<String, String> deptNameMap = sysDeptDao.queryByIds(
                        allRels.stream().map(SysUserDept::getDeptId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
        Map<String, List<SysUserDept>> relByEmp = allRels.stream()
                .collect(Collectors.groupingBy(SysUserDept::getEmployeeId));

        List<UserDeptVo> result = new ArrayList<>();
        for (User user : users) {
            List<SysUserDept> userRels = relByEmp.getOrDefault(user.getEmployeeId(), List.of());
            UserDeptVo vo = new UserDeptVo();
            vo.setId(user.getId());
            vo.setEmployeeId(user.getEmployeeId());
            vo.setRealName(user.getRealName());
            vo.setPhoneNumber(user.getPhoneNumber());
            vo.setEmail(user.getEmail());
            vo.setStatus(user.getStatus());
            vo.setDepts(userRels.stream()
                    .sorted(Comparator.comparing((SysUserDept r) -> !Boolean.TRUE.equals(r.getIsPrimary())))
                    .map(rel -> {
                        UserDeptVo.DeptRef ref = new UserDeptVo.DeptRef();
                        ref.setDeptId(rel.getDeptId());
                        ref.setDeptName(deptNameMap.getOrDefault(rel.getDeptId(), ""));
                        ref.setPrimary(Boolean.TRUE.equals(rel.getIsPrimary()));
                        return ref;
                    })
                    .collect(Collectors.toList()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 分配用户到部门：无任职部门时作为主部门，已有任职部门时追加为兼职
     * <p>部门列表"添加人员"入口</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignUserDept(String employeeId, String deptId) {
        SysDept dept = sysDeptDao.queryById(deptId);
        if (dept == null) {
            throw new NwaySystemException("部门不存在");
        }
        // 租户维度写操作守卫：校验操作者对目标用户和部门的管辖权
        assertCanManageUserAndDept(employeeId, dept);
        List<SysUserDept> existing = userDeptDao.queryByEmployee(employeeId);
        boolean inDept = existing.stream().anyMatch(ud -> ud.getDeptId().equals(deptId));
        if (inDept) {
            return;
        }
        if (CollectionUtils.isEmpty(existing)) {
            // 无任何任职部门：作为主部门（内部方法，已通过外层校验）
            saveUserDeptsInternal(employeeId, deptId, null);
        } else {
            // 已有任职部门：追加兼职（内部方法，已通过外层校验）
            addSecondaryDeptInternal(employeeId, deptId);
        }
    }

    /**
     * 保存用户部门关系（全量覆盖）。
     * <p>
     * 必须指定一个主部门，同时更新 sys_user.dept_id。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserDepts(String employeeId, String primaryDeptId, List<String> secondaryDeptIds) {
        if (StringUtils.isBlank(primaryDeptId)) {
            throw new NwaySystemException("主部门不能为空");
        }
        // 校验主部门存在
        SysDept primaryDept = sysDeptDao.queryById(primaryDeptId);
        if (primaryDept == null) {
            throw new NwaySystemException("主部门不存在");
        }
        // 租户维度写操作守卫：校验操作者对目标用户和主部门的管辖权
        assertCanManageUserAndDept(employeeId, primaryDept);
        // 校验所有兼职部门的租户
        if (!CollectionUtils.isEmpty(secondaryDeptIds)) {
            for (String deptId : secondaryDeptIds) {
                if (deptId.equals(primaryDeptId)) {
                    continue;
                }
                SysDept dept = sysDeptDao.queryById(deptId);
                if (dept == null) {
                    throw new NwaySystemException("兼职部门不存在：" + deptId);
                }
                TenantUtil.assertAdminCanManageTenant(dept.getTenantCode());
            }
        }
        saveUserDeptsInternal(employeeId, primaryDeptId, secondaryDeptIds);
    }

    /**
     * 保存用户部门关系（内部方法，调用前需确保已完成权限校验）
     */
    private void saveUserDeptsInternal(String employeeId, String primaryDeptId, List<String> secondaryDeptIds) {
        // 记录原任职部门，用于联动清理被移除部门的岗位关系
        Set<String> oldDeptIds = userDeptDao.queryByEmployee(employeeId).stream()
                .map(SysUserDept::getDeptId)
                .collect(Collectors.toSet());

        // 清除旧关系
        userDeptDao.deleteByEmployee(employeeId);

        // 保存主部门
        SysUserDept primary = new SysUserDept();
        primary.setEmployeeId(employeeId);
        primary.setDeptId(primaryDeptId);
        primary.setIsPrimary(true);
        userDeptDao.insert(primary);

        // 保存兼职部门
        if (!CollectionUtils.isEmpty(secondaryDeptIds)) {
            for (String deptId : secondaryDeptIds) {
                if (deptId.equals(primaryDeptId)) {
                    continue;
                }
                SysUserDept secondary = new SysUserDept();
                secondary.setEmployeeId(employeeId);
                secondary.setDeptId(deptId);
                secondary.setIsPrimary(false);
                userDeptDao.insert(secondary);
            }
        }

        // 同步更新 sys_user.dept_id（主部门快捷字段）
        User update = new User();
        update.setId(getUserIdByEmployee(employeeId));
        update.setDeptId(primaryDeptId);
        userDao.updateById(update);

        // 联动：被移除的部门上若挂有该用户的岗位，一并清理（岗位必须在任职部门内）
        Set<String> newDeptIds = new HashSet<>(secondaryDeptIds == null ? List.of() : secondaryDeptIds);
        newDeptIds.add(primaryDeptId);
        oldDeptIds.stream()
                .filter(id -> !newDeptIds.contains(id))
                .forEach(id -> userPositionDao.deleteByEmployeeAndDept(employeeId, id));

        // 联动：主部门变更后，若主岗岗位不再属于新主部门，自动取消主岗标记（保留兼岗）
        cancelPrimaryIfDeptMismatch(employeeId, primaryDeptId);
    }

    /**
     * 变更主部门（一键部门调动）
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePrimaryDept(String employeeId, String newPrimaryDeptId) {
        SysDept dept = sysDeptDao.queryById(newPrimaryDeptId);
        if (dept == null) {
            throw new NwaySystemException("部门不存在");
        }
        // 租户维度写操作守卫：校验操作者对目标用户和部门的管辖权
        assertCanManageUserAndDept(employeeId, dept);

        // 清除旧主部门标记
        userDeptDao.clearPrimary(employeeId);

        // 如果新主部门已在兼职列表中，更新为主部门；否则新增
        List<SysUserDept> existingDepts = userDeptDao.queryByEmployee(employeeId);
        SysUserDept existing = existingDepts.stream()
                .filter(ud -> ud.getDeptId().equals(newPrimaryDeptId))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            // 已存在，直接设为主部门（通过删除重建实现，避免 update 复杂性）
            userDeptDao.deleteByEmployeeAndDept(employeeId, newPrimaryDeptId);
        }

        SysUserDept primary = new SysUserDept();
        primary.setEmployeeId(employeeId);
        primary.setDeptId(newPrimaryDeptId);
        primary.setIsPrimary(true);
        userDeptDao.insert(primary);

        // 同步 sys_user.dept_id
        User update = new User();
        update.setId(getUserIdByEmployee(employeeId));
        update.setDeptId(newPrimaryDeptId);
        userDao.updateById(update);

        // 联动：主岗必须位于主部门，主部门变更后若主岗岗位不属于新主部门，自动取消主岗标记
        cancelPrimaryIfDeptMismatch(employeeId, newPrimaryDeptId);
    }

    /**
     * 主部门变更后的主岗联动：主岗岗位部门不等于新主部门时自动取消主岗标记（保留兼岗）
     */
    private void cancelPrimaryIfDeptMismatch(String employeeId, String primaryDeptId) {
        SysUserPosition primary = userPositionDao.queryPrimary(employeeId);
        if (primary == null) {
            return;
        }
        SysPosition position = positionDao.queryById(primary.getPositionId());
        if (position == null || !primaryDeptId.equals(position.getDeptId())) {
            userPositionDao.clearPrimary(employeeId);
        }
    }

    /**
     * 添加兼职部门
     */
    @Transactional(rollbackFor = Exception.class)
    public void addSecondaryDept(String employeeId, String deptId) {
        SysDept dept = sysDeptDao.queryById(deptId);
        if (dept == null) {
            throw new NwaySystemException("部门不存在");
        }
        // 租户维度写操作守卫：校验操作者对目标用户和部门的管辖权
        assertCanManageUserAndDept(employeeId, dept);
        addSecondaryDeptInternal(employeeId, deptId);
    }

    /**
     * 添加兼职部门（内部方法，调用前需确保已完成权限校验）
     */
    private void addSecondaryDeptInternal(String employeeId, String deptId) {
        // 检查是否已存在
        List<SysUserDept> existing = userDeptDao.queryByEmployee(employeeId);
        boolean alreadyExists = existing.stream().anyMatch(ud -> ud.getDeptId().equals(deptId));
        if (alreadyExists) {
            throw new NwaySystemException("用户已在该部门任职");
        }
        SysUserDept userDept = new SysUserDept();
        userDept.setEmployeeId(employeeId);
        userDept.setDeptId(deptId);
        userDept.setIsPrimary(false);
        userDeptDao.insert(userDept);
    }

    /**
     * 移除兼职部门
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeSecondaryDept(String employeeId, String deptId) {
        // 不能移除主部门
        SysUserDept primary = userDeptDao.queryPrimary(employeeId);
        if (primary != null && primary.getDeptId().equals(deptId)) {
            throw new NwaySystemException("不能移除主部门，请先变更主部门");
        }
        // 租户维度写操作守卫：校验操作者对目标用户的管辖权
        User user = userDao.getByEmployeeId(employeeId);
        if (user != null) {
            TenantUtil.assertAdminCanManageTenant(user.getTenantCode());
        }
        userDeptDao.deleteByEmployeeAndDept(employeeId, deptId);
        // 联动：清理该用户在移出部门上的岗位关系（岗位必须在任职部门内）
        userPositionDao.deleteByEmployeeAndDept(employeeId, deptId);
    }

    /**
     * 租户维度写操作守卫：校验操作者对目标用户和部门的管辖权。
     * <p>
     * 管理员必须同时拥有目标用户所属租户和目标部门所属租户的授权。
     */
    private void assertCanManageUserAndDept(String employeeId, SysDept dept) {
        User user = userDao.getByEmployeeId(employeeId);
        if (user == null) {
            throw new NwaySystemException("用户不存在");
        }
        TenantUtil.assertAdminCanManageTenant(user.getTenantCode());
        TenantUtil.assertAdminCanManageTenant(dept.getTenantCode());
    }

    private String getUserIdByEmployee(String employeeId) {
        User user = userDao.getByEmployeeId(employeeId);
        if (user == null) {
            throw new NwaySystemException("用户不存在");
        }
        return user.getId();
    }
}
