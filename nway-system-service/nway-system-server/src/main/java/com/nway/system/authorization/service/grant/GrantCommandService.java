package com.nway.system.authorization.service.grant;

import com.alibaba.fastjson2.JSON;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.authorization.dao.GrantDao;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.authorization.entity.SysGrantChange;
import com.nway.system.identity.dao.SysPositionDao;
import com.nway.system.identity.entity.SysPosition;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 授权写命令（唯一写入口）。
 * <p>
 * 每次写入在同一事务内完成：授权行 → 变更留痕 → 全局版本 +1；提交后清空授权缓存。
 * 这样"谁在什么时候把什么权限给了谁"永远可追溯，且缓存不会出现"旧权限长期不失效"。
 */
@Service
public class GrantCommandService {

    private static final Set<String> SUBJECT_TYPES = Set.of("ROLE", "USER", "POSITION", "APP");
    private static final Set<String> EFFECTS = Set.of(SysGrant.EFFECT_ALLOW, SysGrant.EFFECT_DENY);
    private static final Set<String> SCOPES = Set.of(SysGrant.SCOPE_ALL, SysGrant.SCOPE_SELF,
            SysGrant.SCOPE_DEPT, SysGrant.SCOPE_DEPT_TREE, SysGrant.SCOPE_DEPT_LIST, SysGrant.SCOPE_NONE);
    private static final String WILDCARD_RESOURCE = "*";

    private final GrantDao grantDao;
    private final ResourceDao resourceDao;
    private final RoleDao roleDao;
    private final UserDao userDao;
    private final SysPositionDao positionDao;
    private final AuthzVersionService versionService;
    private final GrantCacheService cacheService;
    private final GrantActionAdvisor actionAdvisor;

    public GrantCommandService(GrantDao grantDao, ResourceDao resourceDao, RoleDao roleDao,
                               UserDao userDao, SysPositionDao positionDao,
                               AuthzVersionService versionService, GrantCacheService cacheService,
                               GrantActionAdvisor actionAdvisor) {
        this.grantDao = grantDao;
        this.resourceDao = resourceDao;
        this.roleDao = roleDao;
        this.userDao = userDao;
        this.positionDao = positionDao;
        this.versionService = versionService;
        this.cacheService = cacheService;
        this.actionAdvisor = actionAdvisor;
    }

    /**
     * 保存单条授权（新增或更新）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String save(SysGrant grant, String reason) {
        validate(grant);
        SysGrant before = StringUtils.isBlank(grant.getId()) ? null : grantDao.getById(grant.getId());
        if (before == null && StringUtils.isBlank(grant.getId())) {
            // upsert：同一主体+资源+效果已有记录时更新，而不是撞唯一键
            before = grantDao.findByKey(grant.getSubjectType(), grant.getSubjectId(),
                    grant.getAppCode(), grant.getResourceCode(), grant.getEffect());
            if (before != null) {
                grant.setId(before.getId());
            }
        }
        if (before != null) {
            grant.setTenantCode(before.getTenantCode());
            grant.setVersion((before.getVersion() == null ? 0 : before.getVersion()) + 1);
        } else {
            grant.setTenantCode(resolveSubjectTenant(grant.getSubjectType(), grant.getSubjectId()));
            grant.setVersion(1L);
        }
        normalize(grant);
        assertCanManage(grant.getTenantCode());

        if (before == null) {
            grantDao.insertOrRevive(grant);
        } else {
            grantDao.updateById(grant);
        }
        long version = versionService.bump();
        writeChange(grant.getSubjectType(), grant.getSubjectId(), before, grant, reason, version);
        cacheService.invalidateAll();
        return grant.getId();
    }

    /**
     * 替换式保存某角色的全部授权（管理端"策略配置"入口）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceRoleGrants(String roleId, List<SysGrant> grants, String reason) {
        Role role = roleDao.queryById(roleId);
        if (role == null) {
            throw new NwaySystemException("角色不存在");
        }
        assertCanManage(role.getTenantCode());

        List<SysGrant> before = grantDao.queryByRole(roleId);
        grantDao.deleteByRole(roleId);
        List<SysGrant> after = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        if (!CollectionUtils.isEmpty(grants)) {
            for (SysGrant grant : grants) {
                grant.setSubjectType("ROLE");
                grant.setSubjectId(roleId);
                grant.setTenantCode(role.getTenantCode());
                grant.setVersion(1L);
                validate(grant);
                normalize(grant);
                if (!keys.add(grantKey(grant))) {
                    // 同一键重复时后续 insertOrRevive 会命中刚复活/插入的行，表现为不可预期的覆盖
                    throw new NwaySystemException("存在重复授权项：" + grant.getAppCode() + ':'
                            + grant.getResourceCode() + "(" + grant.getEffect() + ")");
                }
                // 墓碑行复用：唯一键未含 is_deleted，不能直接 INSERT（D5）
                grantDao.insertOrRevive(grant);
                after.add(grant);
            }
        }
        long version = versionService.bump();
        writeChange("ROLE", roleId, before, after, reason, version);
        cacheService.invalidateAll();
    }

    /**
     * 替换式保存某资源的全部授权（AI 接口权限抽屉）。
     * <p>
     * 资源授权以调用方给出的清单为准：先删除该资源旧授权，再逐条写入；
     * 单事务、一次版本递增、一条 RESOURCE 变更留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceResourceGrants(String appCode, String resourceCode,
                                      List<SysGrant> grants, String reason) {
        if (StringUtils.isBlank(appCode) || StringUtils.isBlank(resourceCode)) {
            throw new NwaySystemException("应用标识与资源标识不能为空");
        }
        List<SysGrant> before = grantDao.queryByResource(appCode, resourceCode);
        grantDao.deleteByResource(appCode, resourceCode);
        List<SysGrant> after = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        if (!CollectionUtils.isEmpty(grants)) {
            for (SysGrant grant : grants) {
                grant.setId(null);
                grant.setAppCode(appCode);
                grant.setResourceCode(resourceCode);
                grant.setVersion(1L);
                grant.setIsDeleted(Boolean.FALSE);
                validate(grant);
                grant.setTenantCode(resolveSubjectTenant(grant.getSubjectType(), grant.getSubjectId()));
                assertCanManage(grant.getTenantCode());
                normalize(grant);
                if (!keys.add(grantKey(grant))) {
                    throw new NwaySystemException("存在重复授权项：" + appCode + ':' + resourceCode
                            + "(" + grant.getSubjectType() + ":" + grant.getSubjectId()
                            + "/" + grant.getEffect() + ")");
                }
                grantDao.insertOrRevive(grant);
                after.add(grant);
            }
        }
        long version = versionService.bump();
        writeChange("RESOURCE", resourceCode, before, after, reason, version);
        cacheService.invalidateAll();
    }

    /**
     * 撤销某资源的全部授权（接口删除时调用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int revokeResourceGrants(String appCode, String resourceCode, String reason) {
        if (StringUtils.isBlank(appCode) || StringUtils.isBlank(resourceCode)) {
            return 0;
        }
        List<SysGrant> before = grantDao.queryByResource(appCode, resourceCode);
        if (before.isEmpty()) {
            return 0;
        }
        grantDao.deleteByResource(appCode, resourceCode);
        long version = versionService.bump();
        writeChange("RESOURCE", resourceCode, before, null, reason, version);
        cacheService.invalidateAll();
        return before.size();
    }

    /**
     * 接口编码变更时迁移授权：把旧资源的授权搬到新资源。
     * <p>
     * 同一主体/效果在新资源上已存在时合并动作（并集），避免改名导致授权静默丢失。
     */
    @Transactional(rollbackFor = Exception.class)
    public void migrateResourceCode(String appCode, String oldResourceCode,
                                    String newResourceCode, String reason) {
        if (StringUtils.isBlank(oldResourceCode) || StringUtils.isBlank(newResourceCode)
                || oldResourceCode.equals(newResourceCode)) {
            return;
        }
        List<SysGrant> before = grantDao.queryByResource(appCode, oldResourceCode);
        if (before.isEmpty()) {
            return;
        }
        List<SysGrant> after = new ArrayList<>();
        for (SysGrant old : before) {
            SysGrant target = grantDao.findByKey(old.getSubjectType(), old.getSubjectId(),
                    appCode, newResourceCode, old.getEffect());
            if (target == null) {
                old.setResourceCode(newResourceCode);
                old.setVersion((old.getVersion() == null ? 0 : old.getVersion()) + 1);
                grantDao.updateById(old);
                after.add(old);
                continue;
            }
            Set<String> actions = new LinkedHashSet<>(parseActions(target.getActions()));
            actions.addAll(parseActions(old.getActions()));
            target.setActions(JSON.toJSONString(actions));
            target.setVersion((target.getVersion() == null ? 0 : target.getVersion()) + 1);
            grantDao.updateById(target);
            grantDao.deleteById(old.getId());
            after.add(target);
        }
        long version = versionService.bump();
        writeChange("RESOURCE", oldResourceCode, before, after, reason, version);
        cacheService.invalidateAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String reason) {
        SysGrant before = grantDao.getById(id);
        if (before == null) {
            return;
        }
        assertCanManage(before.getTenantCode());
        grantDao.deleteById(id);
        long version = versionService.bump();
        writeChange(before.getSubjectType(), before.getSubjectId(), before, null, reason, version);
        cacheService.invalidateAll();
    }

    /**
     * 回滚某次授权变更：把 before 快照重新应用（不支持跨主体回滚）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(String changeId, String reason) {
        SysGrantChange change = grantDao.getChange(changeId);
        if (change == null) {
            throw new NwaySystemException("变更记录不存在：" + changeId);
        }
        String before = change.getBeforeJson();
        String rollbackReason = "rollback:" + (reason == null ? "" : reason);
        if (before != null && before.trim().startsWith("[")) {
            List<SysGrant> grants = JSON.parseArray(before, SysGrant.class);
            if (!"ROLE".equals(change.getTargetType())) {
                throw new NwaySystemException("仅支持回滚角色级批量变更：" + changeId);
            }
            replaceRoleGrants(change.getTargetId(), grants, rollbackReason);
            return;
        }
        if (before == null || before.isBlank()) {
            grantDao.deleteBySubject(change.getTargetType(), change.getTargetId());
            long version = versionService.bump();
            writeChange(change.getTargetType(), change.getTargetId(), null, null, rollbackReason, version);
            cacheService.invalidateAll();
            return;
        }
        SysGrant snapshot = JSON.parseObject(before, SysGrant.class);
        save(snapshot, rollbackReason);
    }

    /**
     * 批量授权：对多个主体应用同一资源动作/范围。
     * <p>
     * 逐条写入（每条独立留痕）；整体在同一事务内，失败全部回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public int batch(String subjectType, List<String> subjectIds, SysGrant template, String reason) {
        if (CollectionUtils.isEmpty(subjectIds)) {
            return 0;
        }
        int count = 0;
        for (String subjectId : subjectIds) {
            SysGrant grant = new SysGrant();
            grant.setSubjectType(subjectType);
            grant.setSubjectId(subjectId);
            grant.setAppCode(template.getAppCode());
            grant.setResourceCode(template.getResourceCode());
            grant.setActions(template.getActions());
            grant.setEffect(template.getEffect());
            grant.setDataScope(template.getDataScope());
            grant.setScopeDeptIds(template.getScopeDeptIds());
            grant.setEffectiveFrom(template.getEffectiveFrom());
            grant.setExpireAt(template.getExpireAt());
            grant.setStatus(template.getStatus());
            save(grant, reason);
            count++;
        }
        return count;
    }

    // ====== 校验与归一 ======

    private void validate(SysGrant grant) {
        if (grant == null || StringUtils.isBlank(grant.getSubjectType())) {
            throw new NwaySystemException("主体类型不能为空");
        }
        grant.setSubjectType(grant.getSubjectType().toUpperCase(Locale.ROOT));
        if (!SUBJECT_TYPES.contains(grant.getSubjectType())) {
            throw new NwaySystemException("不支持的主体类型：" + grant.getSubjectType());
        }
        if (StringUtils.isBlank(grant.getSubjectId())) {
            throw new NwaySystemException("主体id不能为空");
        }
        if (StringUtils.isBlank(grant.getAppCode())) {
            throw new NwaySystemException("应用标识不能为空");
        }
        if (StringUtils.isBlank(grant.getResourceCode())) {
            throw new NwaySystemException("资源标识不能为空");
        }
        String effect = StringUtils.defaultIfBlank(grant.getEffect(), SysGrant.EFFECT_ALLOW)
                .toUpperCase(Locale.ROOT);
        if (!EFFECTS.contains(effect)) {
            throw new NwaySystemException("不支持的授权效果：" + effect);
        }
        grant.setEffect(effect);
        String scope = StringUtils.defaultIfBlank(grant.getDataScope(), SysGrant.SCOPE_SELF)
                .toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            throw new NwaySystemException("不支持的数据范围：" + scope);
        }
        grant.setDataScope(scope);
        List<String> actions = parseActions(grant.getActions());
        if (actions.isEmpty()) {
            throw new NwaySystemException("动作不能为空（全部动作请显式写 [\"*\"]）");
        }
        if (!WILDCARD_RESOURCE.equals(grant.getResourceCode())
                && !resourceDao.exists(grant.getAppCode(), grant.getResourceCode())) {
            throw new NwaySystemException("资源不存在：" + grant.getAppCode() + ":" + grant.getResourceCode()
                    + "，请先在资源登记台登记");
        }
        // 只告警不阻断：写类动作缺少 read 时提醒（避免“能改不能看”的误配）
        actionAdvisor.warnIfWriteWithoutRead(grant.getAppCode(), grant.getResourceCode(), grant.getActions());
    }

    private void normalize(SysGrant grant) {
        String actions = grant.getActions();
        if (StringUtils.isBlank(actions)) {
            actions = "[\"*\"]";
        }
        grant.setActions(actions);
        grant.setStatus(StringUtils.defaultIfBlank(grant.getStatus(), SysGrant.STATUS_ACTIVE)
                .toUpperCase(Locale.ROOT));
        if (SysGrant.SCOPE_DEPT_LIST.equals(grant.getDataScope())) {
            if (parseActions(grant.getScopeDeptIds()).isEmpty()) {
                throw new NwaySystemException("DEPT_LIST 范围必须提供部门编码列表");
            }
        } else {
            grant.setScopeDeptIds(null);
        }
        if (grant.getEffectiveFrom() != null && grant.getExpireAt() != null
                && !grant.getEffectiveFrom().isBefore(grant.getExpireAt())) {
            throw new NwaySystemException("生效时间必须早于失效时间");
        }
    }

    /** 唯一键（与 uidx_grant_subject_resource 对齐），用于去重与墓碑行定位。 */
    private static String grantKey(SysGrant grant) {
        return grant.getSubjectType() + '\u0000' + grant.getSubjectId() + '\u0000'
                + grant.getAppCode() + '\u0000' + grant.getResourceCode() + '\u0000' + grant.getEffect();
    }

    private List<String> parseActions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = JSON.parseArray(json, String.class);
            return list == null ? List.of() : list;
        } catch (RuntimeException e) {
            throw new NwaySystemException("动作必须是 JSON 字符串数组：" + json);
        }
    }

    private String resolveSubjectTenant(String subjectType, String subjectId) {
        String tenant = switch (subjectType) {
            case "ROLE" -> {
                Role role = roleDao.queryById(subjectId);
                if (role == null) {
                    throw new NwaySystemException("角色不存在：" + subjectId);
                }
                yield role.getTenantCode();
            }
            case "USER" -> {
                User user = userDao.getByEmployeeId(subjectId);
                if (user == null) {
                    throw new NwaySystemException("用户不存在：" + subjectId);
                }
                yield user.getTenantCode();
            }
            case "POSITION" -> {
                SysPosition position = positionDao.queryById(subjectId);
                if (position == null) {
                    throw new NwaySystemException("岗位不存在：" + subjectId);
                }
                yield position.getTenantCode();
            }
            case "APP" -> TenantUtil.PUBLIC_TENANT_CODE;
            default -> throw new NwaySystemException("不支持的主体类型：" + subjectType);
        };
        if (StringUtils.isBlank(tenant)) {
            throw new NwaySystemException("主体缺少租户归属：" + subjectType + ":" + subjectId);
        }
        return tenant;
    }

    private void assertCanManage(String tenantCode) {
        if (tenantCode == null) {
            throw new NwaySystemException("无法确定授权租户");
        }
        TenantUtil.assertAdminCanManageTenant(tenantCode);
    }

    private void writeChange(String targetType, String targetId, Object before, Object after,
                             String reason, long version) {
        SysGrantChange change = new SysGrantChange();
        change.setActorId(LoginUserContext.current() != null
                ? LoginUserContext.current().getEmployeeId() : TenantUtil.SYSTEM_EMPLOYEE_ID);
        change.setActorTenant(TenantUtil.currentTenantCode());
        change.setReason(reason);
        change.setTargetType(targetType);
        change.setTargetId(targetId);
        change.setBeforeJson(before == null ? null : JSON.toJSONString(before));
        change.setAfterJson(after == null ? null : JSON.toJSONString(after));
        change.setAuthzVersion(version);
        change.setRequestId(org.slf4j.MDC.get("traceId"));
        change.setCreateTime(LocalDateTime.now());
        grantDao.insertChange(change);
    }
}
