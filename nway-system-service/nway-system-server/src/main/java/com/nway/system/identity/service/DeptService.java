package com.nway.system.identity.service;

import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.identity.web.model.DeptDto;
import com.nway.system.authorization.service.decision.AuthzDecisionService;
import com.nway.system.authorization.service.decision.SubjectContext;
import com.nway.system.authorization.service.decision.SubjectContextResolver;
import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.identity.event.DeptChangedEvent;
import com.nway.system.common.security.LoginUser;
import com.nway.system.authorization.domain.MergedPolicy;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.dao.SysUserDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.identity.entity.SysUserDept;
import com.nway.system.identity.dao.SysPositionDao;
import com.nway.system.identity.dao.SysUserPositionDao;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.identity.web.model.DeptTreeVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 部门管理服务：层级编码自动生成、树构建、CRUD、人员部门关系维护
 */
@Service
@RequiredArgsConstructor
public class DeptService {

    private final SysDeptDao sysDeptDao;
    private final SysUserDeptDao userDeptDao;
    private final UserDao userDao;
    private final TenantDao tenantDao;
    private final SysUserPositionDao userPositionDao;
    private final SysPositionDao positionDao;
    private final PolicyService policyService;
    private final AuthzDecisionService authzDecisionService;
    private final SubjectContextResolver subjectContextResolver;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 查询全部部门并构建树结构
     */
    public List<DeptTreeVO> getTree() {
        List<SysDept> all = sysDeptDao.queryAll();
        if (CollectionUtils.isEmpty(all)) {
            return List.of();
        }
        List<DeptTreeVO> voList = all.stream().map(this::toTreeVO).toList();
        // 填充租户名称
        fillDeptTenantNames(voList);
        return buildTree(voList);
    }

    /**
     * 按租户查询部门树（岗位等跨租户下拉用）。
     * <p>非超管忽略租户参数，直接走自动租户隔离；超管指定租户时仅返回该租户下部门。</p>
     */
    public List<DeptTreeVO> getTreeByTenantCode(String tenantCode) {
        if (StringUtils.isBlank(tenantCode) || !TenantUtil.isSuperAdmin()) {
            return getTree();
        }
        List<SysDept> all = sysDeptDao.queryAllByTenantCode(tenantCode);
        if (CollectionUtils.isEmpty(all)) {
            return List.of();
        }
        List<DeptTreeVO> voList = all.stream().map(this::toTreeVO).toList();
        // 填充租户名称
        fillDeptTenantNames(voList);
        return buildTree(voList);
    }

    /**
     * 按部门 id 集合查询部门树（开放平台数据范围过滤用，不依赖登录上下文）。
     *
     * @param deptIds 允许的部门 id 集合；null 表示不限制（返回全部）
     */
    public List<DeptTreeVO> getTreeByDeptIds(List<String> deptIds) {
        List<SysDept> all;
        if (deptIds == null) {
            all = sysDeptDao.queryAll();
        } else if (deptIds.isEmpty()) {
            return List.of();
        } else {
            all = sysDeptDao.queryByIds(deptIds);
        }
        if (CollectionUtils.isEmpty(all)) {
            return List.of();
        }
        List<DeptTreeVO> voList = all.stream().map(this::toTreeVO).toList();
        // 填充租户名称
        fillDeptTenantNames(voList);
        List<DeptTreeVO> tree = buildTree(voList);
        // 根节点 deptName 前缀标识租户
        markRootTenantLabel(tree);
        return tree;
    }

    /**
     * 查询当前用户权限范围内的部门树，根节点标识租户名称。
     * <p>
     * 权限策略：
     * <ul>
     *   <li>超管：返回全部部门</li>
     *   <li>ALL 范围：返回当前租户隔离后的全部部门</li>
     *   <li>DEPT：仅返回用户主部门</li>
     *   <li>DEPT_WITH_SUB：返回用户主部门及其子树</li>
     *   <li>CUSTOM：返回指定的自定义部门集合及其子树</li>
     *   <li>SELF/无规则：仅返回用户主部门</li>
     * </ul>
     * 树的根节点 deptName 会前缀展示租户名称，如 "[租户A] 总公司"。
     */
    public List<DeptTreeVO> getPermissionScopedTree() {
        LoginUser loginUser = LoginUserContext.current();
        if (loginUser == null) {
            return List.of();
        }

        List<SysDept> scopedDepts;

        if (loginUser.isSuperAdmin()) {
            // 超管：返回全部
            scopedDepts = sysDeptDao.queryAll();
        } else {
            // S2：数据范围一律由决策内核下发（谓词），不再自行读策略字段
            SubjectContext subject = subjectContextResolver.from(loginUser);
            AuthzDecisionService.Decision decision = authzDecisionService.decide(
                    subject, "nway-system", "user", "view");
            scopedDepts = scopedDepts(loginUser, decision);
        }

        if (CollectionUtils.isEmpty(scopedDepts)) {
            return List.of();
        }

        List<DeptTreeVO> voList = scopedDepts.stream().map(this::toTreeVO).toList();
        fillDeptTenantNames(voList);
        List<DeptTreeVO> tree = buildTree(voList);
        // 根节点 deptName 前缀标识租户
        markRootTenantLabel(tree);
        return tree;
    }

    /**
     * 按决策谓词解析可见部门（取最宽一类：ALL > DEPT_TREE > DEPT/DEPT_LIST > SELF > 空）。
     */
    private List<SysDept> scopedDepts(LoginUser loginUser, AuthzDecisionService.Decision decision) {
        if (!decision.allow()) {
            return List.of();
        }
        List<AuthzDecisionService.DataPredicate> predicates = decision.dataPredicates();
        if (predicates.stream().anyMatch(p -> com.nway.system.authorization.entity.SysGrant.SCOPE_ALL.equals(p.kind()))) {
            return sysDeptDao.queryAll();
        }
        for (AuthzDecisionService.DataPredicate predicate : predicates) {
            if (com.nway.system.authorization.entity.SysGrant.SCOPE_DEPT_TREE.equals(predicate.kind())) {
                return predicate.values().stream()
                        .flatMap(code -> sysDeptDao.querySubTree(code).stream())
                        .distinct()
                        .toList();
            }
        }
        for (AuthzDecisionService.DataPredicate predicate : predicates) {
            if (com.nway.system.authorization.entity.SysGrant.SCOPE_DEPT.equals(predicate.kind())
                    || com.nway.system.authorization.entity.SysGrant.SCOPE_DEPT_LIST.equals(predicate.kind())) {
                return sysDeptDao.queryByCodes(predicate.values());
            }
        }
        for (AuthzDecisionService.DataPredicate predicate : predicates) {
            if (com.nway.system.authorization.entity.SysGrant.SCOPE_SELF.equals(predicate.kind())) {
                return resolveSelfDepts(loginUser);
            }
        }
        return List.of();
    }

    /**
     * 根节点 deptName 前缀展示租户名称：[租户名] 部门名
     */
    private void markRootTenantLabel(List<DeptTreeVO> roots) {
        for (DeptTreeVO root : roots) {
            if (StringUtils.isNotBlank(root.getTenantName())) {
                root.setDeptName("[" + root.getTenantName() + "] " + root.getDeptName());
            }
        }
    }

    /**
     * 仅主部门（SELF/DEPT 范围 或 无规则时）
     */
    private List<SysDept> resolveSelfDepts(LoginUser loginUser) {
        if (StringUtils.isBlank(loginUser.getMainDeptId())) {
            return List.of();
        }
        SysDept dept = sysDeptDao.queryById(loginUser.getMainDeptId());
        return dept != null ? List.of(dept) : List.of();
    }

    /**
     * 主部门及其子树（DEPT_WITH_SUB 范围）
     */
    private List<SysDept> resolveDeptWithSub(LoginUser loginUser) {
        if (StringUtils.isBlank(loginUser.getMainDeptCode())) {
            return resolveSelfDepts(loginUser);
        }
        List<SysDept> subTree = sysDeptDao.querySubTree(loginUser.getMainDeptCode());
        return CollectionUtils.isEmpty(subTree) ? List.of() : subTree;
    }

    /**
     * 自定义部门集合及其子树（CUSTOM 范围）
     */
    private List<SysDept> resolveCustomDepts(List<String> deptIds) {
        if (CollectionUtils.isEmpty(deptIds)) {
            return List.of();
        }
        // 查出指定部门
        List<SysDept> baseDepts = sysDeptDao.queryByIds(deptIds);
        if (CollectionUtils.isEmpty(baseDepts)) {
            return List.of();
        }
        // 扩展子树：按每个指定部门的 deptCode 前缀查子树，然后去重
        Set<String> allIds = new LinkedHashSet<>();
        for (SysDept dept : baseDepts) {
            List<SysDept> subTree = sysDeptDao.querySubTree(dept.getDeptCode());
            if (!CollectionUtils.isEmpty(subTree)) {
                subTree.forEach(d -> allIds.add(d.getId()));
            }
        }
        return allIds.isEmpty() ? List.of() : sysDeptDao.queryByIds(allIds);
    }

    /**
     * 保存部门：新增时自动生成或使用自定义层级编码，编辑时更新名称/排序/状态（无子部门时可改编码）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(DeptDto dto) {
        if (StringUtils.isBlank(dto.getDeptName())) {
            throw new NwaySystemException("部门名称不能为空");
        }
        if (StringUtils.isBlank(dto.getId())) {
            insertDept(dto);
        } else {
            updateDept(dto);
        }
    }

    private void insertDept(DeptDto dto) {
        SysDept dept = ConverterUtil.convert(dto, SysDept.class);
        // 根据 parentId 查父部门编码，生成下一级编码
        String parentCode = "";
        if (StringUtils.isNotBlank(dto.getParentId())) {
            SysDept parent = sysDeptDao.queryById(dto.getParentId());
            if (parent == null) {
                throw new NwaySystemException("上级部门不存在");
            }
            parentCode = parent.getDeptCode();
            dept.setParentId(parent.getId());
            // 子部门租户继承父部门（前端不可编辑，后端强制兜底，保证数据一致性）
            dept.setTenantCode(parent.getTenantCode());
        } else {
            dept.setParentId("");
        }
        // 层级编码：允许用户自定义，留空则按同级最大编码+1自动生成
        dept.setDeptCode(resolveDeptCode(dto, parentCode, null));
        // 一致性校验：dept_code 前缀必须与 parent 的 dept_code 一致
        assertDeptCodeParentConsistency(dept.getDeptCode(), dept.getParentId());
        if (dept.getStatus() == null) {
            dept.setStatus("using");
        }
        if (dept.getSort() == null) {
            dept.setSort(0);
        }
        sysDeptDao.insert(dept);
        // 发布部门变更事件
        eventPublisher.publishEvent(new DeptChangedEvent(this, Map.of("deptId", dept.getId(), "action", "created")));
    }

    private void updateDept(DeptDto dto) {
        SysDept exist = sysDeptDao.queryById(dto.getId());
        if (exist == null) {
            throw new NwaySystemException("部门不存在");
        }
        SysDept update = new SysDept();
        update.setId(dto.getId());
        update.setDeptName(dto.getDeptName());
        update.setSort(dto.getSort());
        update.setStatus(dto.getStatus());
        // 层级编码：存在子部门时禁止修改（子部门编码以其为前缀）；无子部门时允许用户自定义
        if (StringUtils.isNotBlank(dto.getDeptCode()) && !dto.getDeptCode().equals(exist.getDeptCode())) {
            if (sysDeptDao.countChildren(dto.getId()) > 0) {
                throw new NwaySystemException("存在子部门，层级编码不可修改");
            }
            String parentCode = "";
            if (StringUtils.isNotBlank(exist.getParentId())) {
                SysDept parent = sysDeptDao.queryById(exist.getParentId());
                parentCode = parent != null ? parent.getDeptCode() : "";
            }
            update.setDeptCode(resolveDeptCode(dto, parentCode, dto.getId()));
            // 一致性校验：dept_code 前缀必须与 parent 的 dept_code 一致
            assertDeptCodeParentConsistency(update.getDeptCode(), exist.getParentId());
        }
        sysDeptDao.updateById(update);
        // 发布部门变更事件
        eventPublisher.publishEvent(new DeptChangedEvent(this, Map.of("deptId", dto.getId(), "action", "updated")));
    }

    /**
     * 解析层级编码：用户自定义时校验格式（父编码长度+2位的数字）与同级唯一，留空则自动生成
     */
    private String resolveDeptCode(DeptDto dto, String parentCode, String excludeId) {
        String input = StringUtils.isBlank(dto.getDeptCode()) ? null : dto.getDeptCode().trim();
        if (input == null) {
            return generateNextCode(parentCode);
        }
        int expectLen = (StringUtils.isNotBlank(parentCode) ? parentCode.length() : 0) + 2;
        if (!input.matches("\\d{" + expectLen + "}")) {
            throw new NwaySystemException("层级编码需为" + expectLen + "位数字（每级2位）");
        }
        boolean conflict = sysDeptDao.queryByCodes(List.of(input)).stream()
                .anyMatch(d -> !d.getId().equals(excludeId));
        if (conflict) {
            throw new NwaySystemException("同级已存在层级编码 " + input);
        }
        return input;
    }

    /**
     * 生成下一级编码：查询同级最大编码 +1，格式化为2位
     */
    private String generateNextCode(String parentCode) {
        String maxCode = sysDeptDao.queryMaxChildCode(parentCode);
        if (maxCode == null) {
            return parentCode + "01";
        }
        String suffix = maxCode.substring(parentCode.length());
        int next = Integer.parseInt(suffix) + 1;
        if (next > 99) {
            throw new NwaySystemException("同级部门数量已达上限(99)");
        }
        return parentCode + String.format("%02d", next);
    }

    /**
     * 删除部门：校验无子部门、无关联用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(String id) {
        SysDept dept = sysDeptDao.queryById(id);
        if (dept == null) {
            throw new NwaySystemException("部门不存在");
        }
        if (sysDeptDao.countChildren(id) > 0) {
            throw new NwaySystemException("存在子部门，无法删除");
        }
        List<User> users = userDao.getByDeptIds(List.of(id));
        if (!CollectionUtils.isEmpty(users)) {
            throw new NwaySystemException("部门下存在用户，无法删除");
        }
        if (!CollectionUtils.isEmpty(userPositionDao.queryByDept(id))) {
            throw new NwaySystemException("部门下存在岗位关联，无法删除");
        }
        if (!CollectionUtils.isEmpty(positionDao.queryByDept(id))) {
            throw new NwaySystemException("部门下存在岗位，无法删除");
        }
        sysDeptDao.deleteById(id);
        // 发布部门变更事件
        eventPublisher.publishEvent(new DeptChangedEvent(this, Map.of("deptId", id, "action", "deleted")));
    }

    /**
     * 分配用户到部门。
     * <p>
     * 已废弃：请使用 {@link UserDeptService#assignUserDept(String, String)} 替代，
     * 该方法同时维护 sys_user.dept_id 和 sys_user_dept 的一致性。
     * <p>
     * 本方法仅更新 sys_user.dept_id，同时同步 sys_user_dept 主部门记录以保证一致性。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignUserDept(String userId, String deptId) {
        User user = userDao.getById(userId);
        if (user == null) {
            throw new NwaySystemException("用户不存在");
        }
        if (StringUtils.isNotBlank(deptId)) {
            SysDept dept = sysDeptDao.queryById(deptId);
            if (dept == null) {
                throw new NwaySystemException("部门不存在");
            }
        }
        User update = new User();
        update.setId(userId);
        update.setDeptId(deptId);
        userDao.updateById(update);

        // 同步 sys_user_dept 主部门记录，保证与 User.deptId 一致
        syncUserDeptPrimary(user.getEmployeeId(), deptId);
    }

    /**
     * 同步 sys_user_dept 的主部门记录与 User.deptId 保持一致。
     * <p>
     * 如果 deptId 为空，清除主部门标记；否则确保存在 is_primary=true 的记录。
     */
    private void syncUserDeptPrimary(String employeeId, String deptId) {
        if (StringUtils.isBlank(employeeId)) {
            return;
        }
        SysUserDept currentPrimary = userDeptDao.queryPrimary(employeeId);
        if (StringUtils.isBlank(deptId)) {
            // 清空主部门
            if (currentPrimary != null) {
                userDeptDao.clearPrimary(employeeId);
            }
            return;
        }
        // 目标部门已是主部门，无需操作
        if (currentPrimary != null && deptId.equals(currentPrimary.getDeptId())) {
            return;
        }
        // 清除旧主部门标记
        if (currentPrimary != null) {
            userDeptDao.clearPrimary(employeeId);
        }
        // 检查目标部门是否已在兼职列表中
        List<SysUserDept> existingDepts = userDeptDao.queryByEmployee(employeeId);
        SysUserDept targetRel = existingDepts.stream()
                .filter(ud -> deptId.equals(ud.getDeptId()))
                .findFirst()
                .orElse(null);
        if (targetRel != null) {
            // 已存在（兼职），删除后重建为主部门
            userDeptDao.deleteByEmployeeAndDept(employeeId, deptId);
        }
        SysUserDept primary = new SysUserDept();
        primary.setEmployeeId(employeeId);
        primary.setDeptId(deptId);
        primary.setIsPrimary(true);
        userDeptDao.insert(primary);
    }

    // ====== 层级编码一致性校验 ======

    /**
     * 校验 dept_code 前缀与 parent_id 指向的父部门 dept_code 一致。
     * <p>
     * 规则：
     * <ul>
     *   <li>顶级部门（parentId 为空）：dept_code 长度应为 2</li>
     *   <li>非顶级：dept_code 前缀 = 父部门 dept_code，且 dept_code 长度 = 父编码长度 + 2</li>
     * </ul>
     *
     * @param deptCode 当前部门层级编码
     * @param parentId 上级部门 id（空串或 null 表示顶级）
     */
    private void assertDeptCodeParentConsistency(String deptCode, String parentId) {
        if (StringUtils.isBlank(deptCode)) {
            return;
        }
        if (StringUtils.isBlank(parentId)) {
            // 顶级部门：编码应为 2 位
            if (deptCode.length() != 2) {
                throw new NwaySystemException("顶级部门层级编码应为 2 位，当前：" + deptCode);
            }
            return;
        }
        SysDept parent = sysDeptDao.queryById(parentId);
        if (parent == null) {
            throw new NwaySystemException("上级部门不存在，无法校验层级编码一致性");
        }
        String parentCode = parent.getDeptCode();
        if (parentCode == null) {
            throw new NwaySystemException("上级部门层级编码为空，数据异常");
        }
        // 子部门 dept_code 必须以父 dept_code 为前缀，且多 2 位
        if (!deptCode.startsWith(parentCode) || deptCode.length() != parentCode.length() + 2) {
            throw new NwaySystemException(
                    "层级编码与上级部门不一致：当前编码=" + deptCode + "，上级编码=" + parentCode
                            + "，预期前缀=" + parentCode + "XX");
        }
    }

    // ====== 树构建工具 ======

    private DeptTreeVO toTreeVO(SysDept dept) {
        DeptTreeVO vo = ConverterUtil.convert(dept, DeptTreeVO.class);
        vo.setLevel(dept.getDeptCode() != null ? dept.getDeptCode().length() / 2 : 1);
        return vo;
    }

    private void fillDeptTenantNames(List<DeptTreeVO> voList) {
        List<String> tenantCodes = voList.stream()
                .map(DeptTreeVO::getTenantCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (tenantCodes.isEmpty()) {
            return;
        }
        Map<String, String> nameMap = tenantCodes.stream()
                .collect(Collectors.toMap(
                        code -> code,
                        code -> {
                            Tenant t = tenantDao.getByCode(code);
                            return t != null ? t.getTenantName() : "";
                        },
                        (a, b) -> a
                ));
        voList.forEach(vo -> {
            if (StringUtils.isNotBlank(vo.getTenantCode())) {
                vo.setTenantName(nameMap.getOrDefault(vo.getTenantCode(), ""));
            }
        });
    }

    /**
     * 将扁平列表构建为树：按 parentId 分组，递归挂载 children
     */
    private List<DeptTreeVO> buildTree(List<DeptTreeVO> all) {
        Map<String, List<DeptTreeVO>> byParent = all.stream()
                .collect(Collectors.groupingBy(v -> v.getParentId() == null ? "" : v.getParentId()));
        List<DeptTreeVO> roots = byParent.getOrDefault("", new ArrayList<>());
        roots.forEach(root -> fillChildren(root, byParent));
        roots.sort(Comparator.comparing(DeptTreeVO::getDeptCode));
        return roots;
    }

    private void fillChildren(DeptTreeVO node, Map<String, List<DeptTreeVO>> byParent) {
        List<DeptTreeVO> children = byParent.getOrDefault(node.getId(), new ArrayList<>());
        children.forEach(child -> fillChildren(child, byParent));
        children.sort(Comparator.comparing(DeptTreeVO::getDeptCode));
        node.setChildren(children);
    }
}
