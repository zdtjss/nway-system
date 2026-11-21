package com.nway.system.openplatform.service;

import com.nway.system.identity.service.UserService;
import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.common.security.LoginUser;
import com.nway.system.authorization.domain.MergedPolicy;
import com.nway.system.openplatform.web.context.OpenAppContext;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.entity.SysDept;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 开放平台数据范围解析服务。
 * <p>
 * 将两层数据范围合并为本次 /open/* 请求的最终范围：
 * <ol>
 *   <li>应用数据范围（sys_open_app.data_scope_type）：client_credentials 模式的唯一约束</li>
 *   <li>代理用户数据权限（统一权限策略 nway-system:user）：user_delegate / authorization_code 模式追加约束</li>
 * </ol>
 * 合并规则：取交集（更严者生效）；任何一环无法确定时 fail-closed（按最小范围处理）。
 *
 * @author nway
 */
@Service
public class OpenDataScopeService {

    /** 平台自身应用编码（代理用户在平台资源上的数据权限归属） */
    private static final String PLATFORM_APP_CODE = "nway-system";
    private static final String PLATFORM_RESOURCE_CODE = "user";

    @Autowired
    private PolicyService policyService;

    @Autowired
    private UserService userService;

    @Autowired
    private SysDeptDao sysDeptDao;

    /**
     * 解析后的开放请求数据范围。
     *
     * @param unrestricted       是否不限制（ALL）
     * @param allowedDeptIds     允许的部门 id 集合（已含子树展开）；受限且为空 = 拒绝所有
     * @param selfOnlyEmployeeId 代理用户 SELF 范围时仅可见本人
     */
    public record OpenDataScope(boolean unrestricted, Set<String> allowedDeptIds, String selfOnlyEmployeeId) {

        public static final OpenDataScope ALL = new OpenDataScope(true, null, null);

        public static OpenDataScope denyAll() {
            return new OpenDataScope(false, Set.of(), null);
        }

        public static OpenDataScope ofDeptIds(List<String> deptIds) {
            return new OpenDataScope(false, deptIds == null ? Set.of() : Set.copyOf(deptIds), null);
        }

        /**
         * 判断部门是否在范围内（unrestricted 时恒真）。
         */
        public boolean allowsDept(String deptId) {
            if (unrestricted) {
                return true;
            }
            return deptId != null && allowedDeptIds.contains(deptId);
        }

        /**
         * 判断用户（按工号 + 主部门）是否在范围内。
         */
        public boolean allowsUser(String employeeId, String mainDeptId) {
            if (unrestricted) {
                return true;
            }
            if (selfOnlyEmployeeId != null) {
                return selfOnlyEmployeeId.equals(employeeId);
            }
            return allowsDept(mainDeptId);
        }
    }

    /**
     * 解析当前 /open/* 请求的最终数据范围。
     */
    public OpenDataScope resolve() {
        return intersect(resolveAppScope(), resolveDelegateUserScope());
    }

    // ==================== 应用数据范围（sys_open_app.data_scope_type） ====================

    /**
     * 应用身份数据范围：ALL 不限制；CUSTOM / DEPT / DEPT_WITH_SUB 均按配置的部门集合解释
     * （应用身份无"本部门"概念，DEPT/DEPT_WITH_SUB 未配置部门集合时 fail-closed 拒绝）。
     * <p>
     * data_scope_type 未配置时按最小范围处理（拒绝所有），不再默认 ALL。
     */
    private OpenDataScope resolveAppScope() {
        OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
        if (appInfo == null) {
            return OpenDataScope.denyAll();
        }
        String scopeType = StringUtils.defaultIfBlank(appInfo.getDataScopeType(), "NONE");
        if ("ALL".equalsIgnoreCase(scopeType)) {
            return OpenDataScope.ALL;
        }
        return expandWithSubTree(appInfo.getDataScopeDeptIds());
    }

    // ==================== 代理用户数据权限（统一权限策略） ====================

    /**
     * 代理用户数据权限：取其在平台（nway-system:user 资源）上的合并策略，
     * 语义与平台内部数据过滤一致（ALL/SELF/DEPT/DEPT_WITH_SUB/CUSTOM，EXPRESSION 回退主部门）。
     */
    private OpenDataScope resolveDelegateUserScope() {
        OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
        if (appInfo == null || StringUtils.isBlank(appInfo.getEmployeeId())) {
            // 无代理用户：仅受应用范围约束
            return OpenDataScope.ALL;
        }
        String employeeId = appInfo.getEmployeeId();
        LoginUser user = userService.getByEmployeeId(employeeId);
        if (user == null) {
            return OpenDataScope.denyAll();
        }
        // 代理用户必须与应用同租户，否则拒绝（防止越租户代理读取）
        if (StringUtils.isNotBlank(appInfo.getTenantCode())
                && !appInfo.getTenantCode().equals(user.getTenantCode())) {
            return OpenDataScope.denyAll();
        }
        if (user.isSuperAdmin()) {
            return OpenDataScope.ALL;
        }

        MergedPolicy policy = policyService.getMergedPolicies(employeeId)
                .get(PLATFORM_APP_CODE + ":" + PLATFORM_RESOURCE_CODE);
        if (policy == null) {
            policy = policyService.getMergedPolicies(employeeId).get(PLATFORM_APP_CODE + ":*");
        }
        String dataScope = policy != null && StringUtils.isNotBlank(policy.getDataScope())
                ? policy.getDataScope().toUpperCase() : "SELF";
        return switch (dataScope) {
            case "ALL" -> OpenDataScope.ALL;
            case "SELF" -> new OpenDataScope(false,
                    user.getMainDeptId() != null ? Set.of(user.getMainDeptId()) : Set.of(), employeeId);
            case "DEPT" -> StringUtils.isNotBlank(user.getMainDeptId())
                    ? OpenDataScope.ofDeptIds(List.of(user.getMainDeptId())) : OpenDataScope.denyAll();
            case "DEPT_WITH_SUB" -> StringUtils.isNotBlank(user.getMainDeptId())
                    ? expandWithSubTree(List.of(user.getMainDeptId())) : OpenDataScope.denyAll();
            case "CUSTOM" -> expandWithSubTree(policy.getDeptIds());
            // EXPRESSION 及未知范围：平台侧按主部门回退（与 DeptService 一致）
            default -> StringUtils.isNotBlank(user.getMainDeptId())
                    ? OpenDataScope.ofDeptIds(List.of(user.getMainDeptId())) : OpenDataScope.denyAll();
        };
    }

    // ==================== 内部方法 ====================

    /**
     * 部门集合及子树展开（按 deptCode 前缀查子树后去重，与平台 CUSTOM 语义一致）。
     */
    private OpenDataScope expandWithSubTree(List<String> deptIds) {
        if (CollectionUtils.isEmpty(deptIds)) {
            return OpenDataScope.denyAll();
        }
        List<SysDept> baseDepts = sysDeptDao.queryByIds(deptIds);
        if (CollectionUtils.isEmpty(baseDepts)) {
            return OpenDataScope.denyAll();
        }
        Set<String> allIds = new LinkedHashSet<>();
        for (SysDept dept : baseDepts) {
            if (StringUtils.isBlank(dept.getDeptCode())) {
                allIds.add(dept.getId());
                continue;
            }
            List<SysDept> subTree = sysDeptDao.querySubTree(dept.getDeptCode());
            if (CollectionUtils.isEmpty(subTree)) {
                allIds.add(dept.getId());
            } else {
                subTree.forEach(d -> allIds.add(d.getId()));
            }
        }
        return OpenDataScope.ofDeptIds(List.copyOf(allIds));
    }

    /**
     * 两层范围取交集（更严者生效）。
     */
    private OpenDataScope intersect(OpenDataScope a, OpenDataScope b) {
        if (a.unrestricted()) {
            return b;
        }
        if (b.unrestricted()) {
            return a;
        }
        String selfOnly = null;
        if (a.selfOnlyEmployeeId() != null && b.selfOnlyEmployeeId() != null) {
            if (!a.selfOnlyEmployeeId().equals(b.selfOnlyEmployeeId())) {
                return OpenDataScope.denyAll();
            }
            selfOnly = a.selfOnlyEmployeeId();
        } else if (a.selfOnlyEmployeeId() != null) {
            selfOnly = a.selfOnlyEmployeeId();
        } else if (b.selfOnlyEmployeeId() != null) {
            selfOnly = b.selfOnlyEmployeeId();
        }
        Set<String> deptIds = new HashSet<>(a.allowedDeptIds());
        deptIds.retainAll(b.allowedDeptIds());
        return new OpenDataScope(false, deptIds, selfOnly);
    }
}
