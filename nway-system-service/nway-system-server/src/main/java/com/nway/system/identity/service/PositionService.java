package com.nway.system.identity.service;

import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.system.authorization.service.policy.PolicyCacheManager;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
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
import com.nway.system.identity.web.model.PositionQuery;
import com.nway.system.identity.web.model.PositionUserVo;
import com.nway.system.identity.web.model.PositionVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 岗位管理服务
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    private final SysPositionDao positionDao;
    private final SysUserPositionDao userPositionDao;
    private final TenantNameResolver tenantNameResolver;
    private final SysUserDeptDao userDeptDao;
    private final SysDeptDao sysDeptDao;
    private final UserDao userDao;
    private final PolicyCacheManager policyCacheManager;

    public List<PositionVo> queryAll() {
        return toVoList(positionDao.queryAll());
    }

    public List<PositionVo> queryByStatus(String status) {
        return toVoList(positionDao.queryByStatus(status));
    }

    public PageVo<PositionVo> queryPage(PositionQuery query) {
        com.nway.spring.jdbc.pagination.Page<SysPosition> page = positionDao.queryPage(query);
        PageVo<PositionVo> pageVo = ConverterUtil.convert(page, PositionVo.class);
        fillNames(pageVo.getData());
        return pageVo;
    }

    /**
     * 实体列表转 VO 并批量填充租户名称、所属部门名称
     */
    private List<PositionVo> toVoList(List<SysPosition> positions) {
        if (CollectionUtils.isEmpty(positions)) {
            return List.of();
        }
        List<PositionVo> voList = ConverterUtil.convert(positions, PositionVo.class);
        fillNames(voList);
        return voList;
    }

    /**
     * 批量填充岗位列表的租户名称、所属部门名称
     */
    private void fillNames(List<PositionVo> voList) {
        if (CollectionUtils.isEmpty(voList)) {
            return;
        }
        Map<String, String> tenantNameMap = tenantNameResolver.resolveNames(
                voList.stream().map(PositionVo::getTenantCode).collect(Collectors.toSet()));
        voList.forEach(vo -> {
            if (StringUtils.isNotBlank(vo.getTenantCode())) {
                vo.setTenantName(tenantNameMap.getOrDefault(vo.getTenantCode(), ""));
            }
        });
        // 填充所属部门名称
        Set<String> deptIds = voList.stream().map(PositionVo::getDeptId)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Map<String, String> deptNameMap = sysDeptDao.queryByIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
        voList.forEach(vo -> {
            if (StringUtils.isNotBlank(vo.getDeptId())) {
                vo.setDeptName(deptNameMap.getOrDefault(vo.getDeptId(), ""));
            }
        });
    }

    public SysPosition getById(String id) {
        return positionDao.queryById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(SysPosition position) {
        if (StringUtils.isBlank(position.getPositionName())) {
            throw new NwaySystemException("岗位名称不能为空");
        }
        if (StringUtils.isBlank(position.getPositionCode())) {
            throw new NwaySystemException("岗位编码不能为空");
        }
        if (StringUtils.isBlank(position.getDeptId())) {
            throw new NwaySystemException("所属部门不能为空");
        }
        if (sysDeptDao.queryById(position.getDeptId()) == null) {
            throw new NwaySystemException("所属部门不存在");
        }
        if (StringUtils.isBlank(position.getId())) {
            // 新增：校验编码唯一
            SysPosition exist = positionDao.queryByCode(position.getPositionCode());
            if (exist != null) {
                throw new NwaySystemException("岗位编码已存在");
            }
            if (position.getStatus() == null) {
                position.setStatus("using");
            }
            positionDao.insert(position);
        } else {
            SysPosition exist = positionDao.queryById(position.getId());
            if (exist == null) {
                throw new NwaySystemException("岗位不存在");
            }
            // 归属租户不可通过更新接口变更：以库中记录为准并校验写权限（UPDATE 的 SET 值不受数据层隔离约束）
            position.setTenantCode(exist.getTenantCode());
            TenantUtil.assertAdminCanManageTenant(exist.getTenantCode());
            positionDao.updateById(position);
            // 岗位变更所属部门：联动同步用户岗位关系冗余 dept_id，并校验/取消受影响主岗
            if (!position.getDeptId().equals(exist.getDeptId())) {
                syncPositionDeptChanged(position);
            }
        }
    }

    /**
     * 岗位改挂部门后的联动：
     * <p>① 该岗位所有用户关系的冗余 dept_id 同步为新部门；
     * ② 若该岗位是某用户的主岗且新部门不再是其主部门，自动取消主岗标记。</p>
     */
    private void syncPositionDeptChanged(SysPosition position) {
        List<SysUserPosition> rels = userPositionDao.queryByPosition(position.getId());
        if (CollectionUtils.isEmpty(rels)) {
            return;
        }
        for (SysUserPosition rel : rels) {
            rel.setDeptId(position.getDeptId());
            userPositionDao.updateById(rel);
            if (Boolean.TRUE.equals(rel.getIsPrimary())) {
                UserDeptInfo info = loadUserDeptInfo(rel.getEmployeeId());
                if (!position.getDeptId().equals(info.primaryDeptId)) {
                    // 主岗岗位不再属于该用户主部门：自动取消主岗标记（保留兼岗）
                    userPositionDao.clearPrimary(rel.getEmployeeId());
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteById(String id) {
        // 校验是否有用户关联
        List<SysUserPosition> users = userPositionDao.queryByPosition(id);
        if (!CollectionUtils.isEmpty(users)) {
            throw new NwaySystemException("该岗位已关联用户，无法删除");
        }
        positionDao.deleteById(id);
    }

    /**
     * 保存用户岗位关联（全量覆盖）。
     * <p>规则：岗位部门由岗位唯一决定（忽略前端传入）；主岗唯一且必须位于用户主部门；
     * 岗位必须属于用户任职部门。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserPositions(String employeeId, List<SysUserPosition> positions) {
        // 租户维度写操作守卫：校验操作者对目标用户的管辖权
        assertCanManageUser(employeeId);
        UserDeptInfo deptInfo = loadUserDeptInfo(employeeId);
        boolean hasPrimary = false;
        if (!CollectionUtils.isEmpty(positions)) {
            for (SysUserPosition up : positions) {
                up.setEmployeeId(employeeId);
                SysPosition position = positionDao.queryById(up.getPositionId());
                if (position == null) {
                    throw new NwaySystemException("岗位不存在");
                }
                // 岗位部门由岗位唯一决定（冗余同步）
                up.setDeptId(position.getDeptId());
                boolean primary = Boolean.TRUE.equals(up.getIsPrimary());
                if (primary) {
                    if (hasPrimary) {
                        throw new NwaySystemException("只能设置一个主岗");
                    }
                    hasPrimary = true;
                }
                validatePositionDept(primary, position.getDeptId(), deptInfo);
            }
        }
        userPositionDao.deleteByEmployee(employeeId);
        if (!CollectionUtils.isEmpty(positions)) {
            userPositionDao.batchInsert(positions);
        }
        // 岗位策略随用户岗位绑定生效，变更后必须失效该用户的合并策略缓存（事务提交后）
        policyCacheManager.evictAfterCommit(employeeId);
    }

    /**
     * 保存单条用户岗位关联（岗位列表"人员管理"入口）
     * <p>已有同岗位关联时更新，否则新增；设置主岗时自动清除该用户其他主岗。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserPosition(SysUserPosition up) {
        if (StringUtils.isBlank(up.getEmployeeId()) || StringUtils.isBlank(up.getPositionId())) {
            throw new NwaySystemException("员工和岗位不能为空");
        }
        // 租户维度写操作守卫：校验操作者对目标用户的管辖权
        assertCanManageUser(up.getEmployeeId());
        SysPosition position = positionDao.queryById(up.getPositionId());
        if (position == null) {
            throw new NwaySystemException("岗位不存在");
        }
        UserDeptInfo deptInfo = loadUserDeptInfo(up.getEmployeeId());
        boolean primary = Boolean.TRUE.equals(up.getIsPrimary());
        validatePositionDept(primary, position.getDeptId(), deptInfo);
        // 岗位部门由岗位唯一决定（冗余同步）
        up.setDeptId(position.getDeptId());

        SysUserPosition exist = userPositionDao.queryByEmployeeAndPosition(up.getEmployeeId(), up.getPositionId());
        if (primary) {
            // 主岗全局唯一：先清除其他主岗标记
            userPositionDao.clearPrimary(up.getEmployeeId());
        }
        if (exist != null) {
            exist.setDeptId(up.getDeptId());
            exist.setIsPrimary(primary);
            userPositionDao.updateById(exist);
        } else {
            userPositionDao.insert(up);
        }
        policyCacheManager.evictAfterCommit(up.getEmployeeId());
    }

    /**
     * 移除单条用户岗位关联（主岗需先取消主岗标记）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeUserPosition(String employeeId, String positionId) {
        // 租户维度写操作守卫：校验操作者对目标用户的管辖权
        assertCanManageUser(employeeId);
        SysUserPosition exist = userPositionDao.queryByEmployeeAndPosition(employeeId, positionId);
        if (exist == null) {
            return;
        }
        if (Boolean.TRUE.equals(exist.getIsPrimary())) {
            throw new NwaySystemException("主岗不能直接移除，请先取消主岗标记");
        }
        userPositionDao.deleteByEmployeeAndPosition(employeeId, positionId);
        policyCacheManager.evictAfterCommit(employeeId);
    }

    /**
     * 查询用户的岗位列表
     */
    public List<SysUserPosition> queryUserPositions(String employeeId) {
        return userPositionDao.queryByEmployee(employeeId);
    }

    /**
     * 按岗位查人（含用户姓名与部门名称）
     */
    public List<PositionUserVo> queryUsersByPosition(String positionId) {
        List<SysUserPosition> rels = userPositionDao.queryByPosition(positionId);
        if (CollectionUtils.isEmpty(rels)) {
            return List.of();
        }
        Set<String> empIds = rels.stream().map(SysUserPosition::getEmployeeId).collect(Collectors.toSet());
        Map<String, User> userMap = userDao.getByEmpids(empIds).stream()
                .collect(Collectors.toMap(User::getEmployeeId, Function.identity()));
        Set<String> deptIds = rels.stream().map(SysUserPosition::getDeptId)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Map<String, String> deptNameMap = sysDeptDao.queryByIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
        return rels.stream().map(rel -> {
            PositionUserVo vo = new PositionUserVo();
            vo.setId(rel.getId());
            vo.setEmployeeId(rel.getEmployeeId());
            vo.setPositionId(rel.getPositionId());
            vo.setDeptId(rel.getDeptId());
            vo.setDeptName(StringUtils.isNotBlank(rel.getDeptId())
                    ? deptNameMap.getOrDefault(rel.getDeptId(), "") : "");
            vo.setIsPrimary(Boolean.TRUE.equals(rel.getIsPrimary()));
            User user = userMap.get(rel.getEmployeeId());
            vo.setRealName(user != null ? user.getRealName() : "");
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 校验岗位与任职部门一致性：岗位必须属于用户任职部门；主岗岗位必须位于用户主部门
     */
    private void validatePositionDept(boolean primary, String positionDeptId, UserDeptInfo deptInfo) {
        if (!deptInfo.deptIds.contains(positionDeptId)) {
            throw new NwaySystemException("该岗位属于未任职的部门，无法担任");
        }
        if (primary) {
            if (deptInfo.primaryDeptId == null) {
                throw new NwaySystemException("用户未设置主部门，无法设置主岗");
            }
            if (!deptInfo.primaryDeptId.equals(positionDeptId)) {
                throw new NwaySystemException("主岗必须位于主部门下（该岗位属于其他部门）");
            }
        }
    }

    /**
     * 租户维度写操作守卫：校验操作者对目标用户的管辖权。
     */
    private void assertCanManageUser(String employeeId) {
        User user = userDao.getByEmployeeId(employeeId);
        if (user == null) {
            throw new NwaySystemException("用户不存在");
        }
        TenantUtil.assertAdminCanManageTenant(user.getTenantCode());
    }

    /**
     * 加载用户任职部门集合与主部门（兼容历史数据：sys_user.dept_id 兜底）
     */
    private UserDeptInfo loadUserDeptInfo(String employeeId) {
        List<SysUserDept> depts = userDeptDao.queryByEmployee(employeeId);
        Set<String> deptIds = depts.stream().map(SysUserDept::getDeptId).collect(Collectors.toSet());
        String primaryDeptId = depts.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsPrimary()))
                .map(SysUserDept::getDeptId)
                .findFirst()
                .orElse(null);
        if (primaryDeptId == null) {
            User user = userDao.getByEmployeeId(employeeId);
            if (user != null && StringUtils.isNotBlank(user.getDeptId())) {
                primaryDeptId = user.getDeptId();
                deptIds.add(primaryDeptId);
            }
        }
        return new UserDeptInfo(deptIds, primaryDeptId);
    }

    /** 用户任职部门信息 */
    private record UserDeptInfo(Set<String> deptIds, String primaryDeptId) {
    }
}
