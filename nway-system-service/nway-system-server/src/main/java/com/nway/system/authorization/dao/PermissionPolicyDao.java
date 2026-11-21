package com.nway.system.authorization.dao;

import com.nway.system.authorization.dao.GrantDao;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.entity.PermissionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 统一权限策略 DAO（对外保持 {@link PermissionPolicy} 视图）。
 * <p>
 * S2 起物理存储切换为 {@code sys_grant}：不再有 {@code actions=null 表示全部}，
 * 旧值在写入时归一为 {@code ["*"]}；查询只返回 ALLOW 且当前生效的授权
 * （DENY 由决策内核 {@code AuthzDecisionService} 处理，不进入合并视图）。
 * <p>
 * 保持本类签名不变，是为了让既有管理端（/policy/*）与菜单可见性逻辑在 S2 期间平滑过渡；
 * S3 授权运营台落地后直接使用 GrantService。
 */
@Repository
public class PermissionPolicyDao {

    @Autowired
    private GrantDao grantDao;

    public void insert(PermissionPolicy policy) {
        SysGrant grant = toGrant(policy);
        // 替换式保存会先逻辑删除旧策略；唯一键未含 is_deleted，必须复用墓碑行（D5）
        grantDao.insertOrRevive(grant);
        policy.setId(grant.getId());
    }

    public void batchInsert(List<PermissionPolicy> policies) {
        if (CollectionUtils.isEmpty(policies)) {
            return;
        }
        for (PermissionPolicy policy : policies) {
            insert(policy);
        }
    }

    public void updateById(PermissionPolicy policy) {
        grantDao.updateById(toGrant(policy));
    }

    public PermissionPolicy getById(String id) {
        return toPolicy(grantDao.getById(id));
    }

    public void deleteById(String id) {
        grantDao.deleteById(id);
    }

    // ====== 按主体查询 ======

    public List<PermissionPolicy> queryBySubject(String subjectType, String subjectId) {
        return convert(grantDao.queryBySubject(subjectType, subjectId));
    }

    public List<PermissionPolicy> queryBySubjects(String subjectType, Collection<String> subjectIds) {
        if (CollectionUtils.isEmpty(subjectIds)) {
            return Collections.emptyList();
        }
        return convert(grantDao.queryBySubjects(subjectType, subjectIds));
    }

    // ====== 按资源查询 ======

    public List<PermissionPolicy> queryByResource(String appCode, String resourceCode) {
        return convert(grantDao.queryByResource(appCode, resourceCode));
    }

    public List<PermissionPolicy> queryByApp(String appCode) {
        return convert(grantDao.queryBySubject("APP", appCode)).stream().toList();
    }

    // ====== 按角色/用户/岗位查询 ======

    public List<PermissionPolicy> queryByRole(String roleId) {
        return queryBySubject("ROLE", roleId);
    }

    public List<PermissionPolicy> queryByRoles(Collection<String> roleIds) {
        return queryBySubjects("ROLE", roleIds);
    }

    public List<PermissionPolicy> queryByUser(String employeeId) {
        return queryBySubject("USER", employeeId);
    }

    public List<PermissionPolicy> queryByPositions(Collection<String> positionIds) {
        return queryBySubjects("POSITION", positionIds);
    }

    public List<PermissionPolicy> queryByAppSubject(String appCode) {
        return queryBySubject("APP", appCode);
    }

    // ====== 删除 ======

    public void deleteBySubject(String subjectType, String subjectId) {
        grantDao.deleteBySubject(subjectType, subjectId);
    }

    public void deleteByRole(String roleId) {
        grantDao.deleteByRole(roleId);
    }

    public void deleteBySubjectAndResource(String subjectType, String subjectId,
                                           String appCode, String resourceCode) {
        grantDao.deleteBySubjectAndResource(subjectType, subjectId, appCode, resourceCode);
    }

    // ====== 视图转换 ======

    private List<PermissionPolicy> convert(List<SysGrant> grants) {
        return grants.stream().map(this::toPolicy).filter(java.util.Objects::nonNull).toList();
    }

    private PermissionPolicy toPolicy(SysGrant grant) {
        if (grant == null) {
            return null;
        }
        PermissionPolicy policy = new PermissionPolicy();
        policy.setId(grant.getId());
        policy.setSubjectType(grant.getSubjectType());
        policy.setSubjectId(grant.getSubjectId());
        policy.setAppCode(grant.getAppCode());
        policy.setResourceCode(grant.getResourceCode());
        policy.setActions(grant.getActions());
        policy.setDataScope(fromGrantScope(grant.getDataScope()));
        policy.setScopeDeptIds(grant.getScopeDeptIds());
        policy.setTenantCode(grant.getTenantCode());
        policy.setCreatorId(grant.getCreatorId());
        policy.setCreatorName(grant.getCreatorName());
        policy.setModifierId(grant.getModifierId());
        policy.setModifierName(grant.getModifierName());
        policy.setCreateTime(grant.getCreateTime());
        policy.setModifiedTime(grant.getModifiedTime());
        policy.setIsDeleted(grant.getIsDeleted());
        return policy;
    }

    private SysGrant toGrant(PermissionPolicy policy) {
        SysGrant grant = new SysGrant();
        grant.setId(policy.getId());
        grant.setSubjectType(policy.getSubjectType());
        grant.setSubjectId(policy.getSubjectId());
        grant.setAppCode(policy.getAppCode());
        grant.setResourceCode(policy.getResourceCode());
        // 旧语义 actions=null 表示全部：写入时显式化为 ["*"]
        grant.setActions(org.apache.commons.lang3.StringUtils.isBlank(policy.getActions())
                ? "[\"*\"]" : policy.getActions());
        grant.setEffect(SysGrant.EFFECT_ALLOW);
        grant.setDataScope(toGrantScope(policy.getDataScope()));
        grant.setScopeDeptIds(policy.getScopeDeptIds());
        grant.setStatus(SysGrant.STATUS_ACTIVE);
        grant.setVersion(1L);
        grant.setTenantCode(policy.getTenantCode());
        grant.setCreatorId(policy.getCreatorId());
        grant.setCreatorName(policy.getCreatorName());
        grant.setModifierId(policy.getModifierId());
        grant.setModifierName(policy.getModifierName());
        grant.setCreateTime(policy.getCreateTime());
        grant.setModifiedTime(policy.getModifiedTime());
        grant.setIsDeleted(Boolean.FALSE);
        return grant;
    }

    private static String toGrantScope(String scope) {
        if (scope == null) {
            return SysGrant.SCOPE_SELF;
        }
        return switch (scope.toUpperCase()) {
            case "DEPT_WITH_SUB" -> SysGrant.SCOPE_DEPT_TREE;
            case "CUSTOM" -> SysGrant.SCOPE_DEPT_LIST;
            case "EXPRESSION" -> SysGrant.SCOPE_SELF; // 表达式未实现，按最小范围处理
            case "ALL" -> SysGrant.SCOPE_ALL;
            case "DEPT" -> SysGrant.SCOPE_DEPT;
            case "NONE" -> SysGrant.SCOPE_NONE;
            case "DEPT_TREE" -> SysGrant.SCOPE_DEPT_TREE;
            case "DEPT_LIST" -> SysGrant.SCOPE_DEPT_LIST;
            default -> SysGrant.SCOPE_SELF;
        };
    }

    private static String fromGrantScope(String scope) {
        if (scope == null) {
            return "SELF";
        }
        return switch (scope) {
            case SysGrant.SCOPE_DEPT_TREE -> "DEPT_WITH_SUB";
            case SysGrant.SCOPE_DEPT_LIST -> "CUSTOM";
            default -> scope;
        };
    }
}
