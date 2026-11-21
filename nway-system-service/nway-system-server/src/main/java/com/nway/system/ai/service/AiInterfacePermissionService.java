package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.authorization.service.decision.AuthzDecisionService;
import com.nway.system.authorization.service.decision.AuthzPreviewService;
import com.nway.system.authorization.service.grant.GrantCommandService;
import com.nway.system.authorization.service.decision.SubjectContext;
import com.nway.system.authorization.service.decision.SubjectContextResolver;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.ai.dao.AiInterfaceDao;
import com.nway.system.ai.entity.AiInterface;
import com.nway.system.authorization.dao.GrantDao;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.identity.dao.SysPositionDao;
import com.nway.system.identity.entity.SysPosition;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import com.nway.system.identity.dao.RoleDao;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI 接口权限管理：页内授权、诊断与资源生命周期。
 * <p>
 * 授权仍写入统一 {@code sys_grant}，只把“资源码/动作”这些机械信息收敛到后端，
 * 管理端只需要选择主体与访问模式。
 *
 * @author nway
 */
@Service
public class AiInterfacePermissionService {

    private static final String APP_CODE = AiPermissionService.APP_CODE;

    private static final String ACTION = AiPermissionService.ACTION;

    private static final String GROUP_RESOURCE_CODE = AiInterfacePermissionRegistrar.GROUP_RESOURCE_CODE;

    private static final Set<String> SUBJECT_TYPES = Set.of("ROLE", "USER", "POSITION");

    private static final Set<String> ACCESS_MODES = Set.of(
            AiPermissionService.MODE_PUBLIC, AiPermissionService.MODE_RESTRICTED);

    private final AiInterfaceDao interfaceDao;
    private final GrantDao grantDao;
    private final GrantCommandService grantCommandService;
    private final AiInterfacePermissionRegistrar registrar;
    private final ResourceDao resourceDao;
    private final AuthzDecisionService decisionService;
    private final AuthzPreviewService previewService;
    private final SubjectContextResolver subjectContextResolver;
    private final RoleDao roleDao;
    private final UserDao userDao;
    private final SysPositionDao positionDao;

    public AiInterfacePermissionService(AiInterfaceDao interfaceDao,
                                        GrantDao grantDao,
                                        GrantCommandService grantCommandService,
                                        AiInterfacePermissionRegistrar registrar,
                                        ResourceDao resourceDao,
                                        AuthzDecisionService decisionService,
                                        AuthzPreviewService previewService,
                                        SubjectContextResolver subjectContextResolver,
                                        RoleDao roleDao,
                                        UserDao userDao,
                                        SysPositionDao positionDao) {
        this.interfaceDao = interfaceDao;
        this.grantDao = grantDao;
        this.grantCommandService = grantCommandService;
        this.registrar = registrar;
        this.resourceDao = resourceDao;
        this.decisionService = decisionService;
        this.previewService = previewService;
        this.subjectContextResolver = subjectContextResolver;
        this.roleDao = roleDao;
        this.userDao = userDao;
        this.positionDao = positionDao;
    }

    /** 管理端授权项：scope=INTERFACE 作用于当前接口，ALL 作用于 ai:* 分组。 */
    public record GrantPayload(String subjectType, String subjectId, String effect) {
    }

    public record GrantItem(String id, String subjectType, String subjectId, String subjectName,
                            String effect, String actions, String tenantCode, String scope) {
    }

    public record PermissionDetail(String id, String interfaceCode, String resourceCode,
                                   String accessMode, boolean resourceRegistered,
                                   String resourceTenantScope, List<GrantItem> grants) {
    }

    public record PermissionCheck(String employeeId, String tenantCode, String interfaceCode,
                                  String resourceCode, String accessMode, boolean allowed,
                                  String reason, String decisionId, List<String> matchedGrantIds,
                                  boolean resourceRegistered, String resourceTenantScope,
                                  int grantCount, int allGrantCount) {
    }

    public record PermissionPreview(int affectedUsers, List<String> sampleUsers) {
    }

    /** 保存前影响面预览：授权主体覆盖的去重工号数。 */
    public PermissionPreview preview(List<GrantPayload> grants, List<GrantPayload> allGrants) {
        Set<String> employees = new LinkedHashSet<>();
        collectEmployees(grants, employees);
        collectEmployees(allGrants, employees);
        return new PermissionPreview(employees.size(), employees.stream().limit(10).toList());
    }

    private void collectEmployees(List<GrantPayload> grants, Set<String> employees) {
        if (grants == null) {
            return;
        }
        for (GrantPayload payload : grants) {
            if (payload == null || StringUtils.isBlank(payload.subjectId())) {
                continue;
            }
            employees.addAll(previewService.affectedUsers(payload.subjectType(), payload.subjectId()));
        }
    }

    public PermissionDetail detail(String interfaceId) {
        return detail(interfaceDao.queryById(interfaceId));
    }

    public PermissionDetail detail(AiInterface entity) {
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在");
        }
        String resourceCode = AiResourceCodes.of(entity.getInterfaceCode());
        SysResource resource = resourceDao.getByAppAndCode(APP_CODE, resourceCode);
        List<GrantItem> grants = new ArrayList<>();
        grants.addAll(items(grantDao.queryByResource(APP_CODE, resourceCode), "INTERFACE"));
        grants.addAll(items(grantDao.queryByResource(APP_CODE, GROUP_RESOURCE_CODE), "ALL"));
        return new PermissionDetail(entity.getId(), entity.getInterfaceCode(), resourceCode,
                defaultMode(entity.getAccessMode()), resource != null,
                resource == null ? null : resource.getTenantScope(), grants);
    }

    /**
     * 保存访问模式与授权清单。{@code grants}/{@code allGrants} 为 null 表示不改动该作用域。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(String interfaceId, String accessMode, List<GrantPayload> grants,
                     List<GrantPayload> allGrants) {
        AiInterface entity = interfaceDao.queryById(interfaceId);
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在");
        }
        TenantUtil.assertAdminCanManageTenant(entity.getTenantCode());
        String mode = StringUtils.isBlank(accessMode)
                ? defaultMode(entity.getAccessMode())
                : accessMode.trim().toUpperCase(Locale.ROOT);
        if (!ACCESS_MODES.contains(mode)) {
            throw new NwaySystemException("访问模式仅支持 PUBLIC/RESTRICTED");
        }
        entity.setAccessMode(mode);
        interfaceDao.updateById(entity);
        if (AiPermissionService.MODE_RESTRICTED.equals(mode) || grants != null || allGrants != null) {
            registrar.synchronize(entity);
        }
        if (grants != null) {
            grantCommandService.replaceResourceGrants(APP_CODE,
                    AiResourceCodes.of(entity.getInterfaceCode()), toGrants(grants),
                    "AI 接口授权：" + entity.getInterfaceCode());
        }
        if (allGrants != null) {
            grantCommandService.replaceResourceGrants(APP_CODE, GROUP_RESOURCE_CODE,
                    toGrants(allGrants), "AI 接口分组授权：全部 AI 接口");
        }
    }

    /** 接口编码变更：授权随资源迁移，旧资源下线。 */
    public void rename(String oldInterfaceCode, AiInterface entity) {
        if (StringUtils.isBlank(oldInterfaceCode) || entity == null
                || oldInterfaceCode.equals(entity.getInterfaceCode())) {
            registrar.synchronize(entity);
            return;
        }
        registrar.synchronize(entity);
        grantCommandService.migrateResourceCode(APP_CODE, AiResourceCodes.of(oldInterfaceCode),
                AiResourceCodes.of(entity.getInterfaceCode()),
                "AI 接口编码变更：" + oldInterfaceCode + " -> " + entity.getInterfaceCode());
        registrar.deprecate(oldInterfaceCode);
    }

    /** 接口删除：撤销本接口授权并下线资源；ai:* 分组授权不受影响。 */
    public void revoke(AiInterface entity) {
        if (entity == null || StringUtils.isBlank(entity.getInterfaceCode())) {
            return;
        }
        grantCommandService.revokeResourceGrants(APP_CODE,
                AiResourceCodes.of(entity.getInterfaceCode()), "AI 接口删除：" + entity.getInterfaceCode());
        registrar.deprecate(entity.getInterfaceCode());
    }

    /**
     * 诊断：某工号为什么能/不能调用该接口。
     */
    public PermissionCheck check(String employeeId, String interfaceId) {
        return check(employeeId, interfaceDao.queryById(interfaceId));
    }

    public PermissionCheck check(String employeeId, AiInterface entity) {
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在");
        }
        User target = TenantUtil.callAsSystem(() -> userDao.getByEmployeeId(employeeId));
        if (target == null) {
            throw new NwaySystemException("用户不存在：" + employeeId);
        }
        TenantUtil.assertAdminCanManageTenant(target.getTenantCode());

        String resourceCode = AiResourceCodes.of(entity.getInterfaceCode());
        SysResource resource = resourceDao.getByAppAndCode(APP_CODE, resourceCode);
        String mode = defaultMode(entity.getAccessMode());
        int grantCount = subjects(grantDao.queryByResource(APP_CODE, resourceCode));
        int allGrantCount = subjects(grantDao.queryByResource(APP_CODE, GROUP_RESOURCE_CODE));

        SubjectContext subject = subjectContextResolver.fromEmployeeId(employeeId);
        if (subject == null) {
            throw new NwaySystemException("无法构建用户上下文：" + employeeId);
        }
        boolean allowed;
        String reason;
        String decisionId = null;
        List<String> matched = List.of();
        if (subject.superAdmin()) {
            allowed = true;
            reason = "super-admin";
        } else if (AiPermissionService.MODE_PUBLIC.equals(mode)) {
            allowed = true;
            reason = "public";
        } else {
            AuthzDecisionService.Decision decision =
                    decisionService.decide(subject, APP_CODE, resourceCode, ACTION);
            allowed = decision.allow();
            reason = decision.reason();
            decisionId = decision.decisionId();
            matched = decision.matchedGrantIds();
        }
        return new PermissionCheck(employeeId, target.getTenantCode(), entity.getInterfaceCode(),
                resourceCode, mode, allowed, reason, decisionId, matched,
                resource != null, resource == null ? null : resource.getTenantScope(),
                grantCount, allGrantCount);
    }

    private List<SysGrant> toGrants(List<GrantPayload> payloads) {
        List<SysGrant> grants = new ArrayList<>();
        for (GrantPayload payload : payloads) {
            String subjectType = StringUtils.upperCase(StringUtils.trimToEmpty(payload.subjectType()));
            if (!SUBJECT_TYPES.contains(subjectType)) {
                throw new NwaySystemException("仅支持对角色/用户/岗位授权：" + payload.subjectType());
            }
            String subjectId = StringUtils.trimToEmpty(payload.subjectId());
            if (subjectId.isEmpty()) {
                throw new NwaySystemException("授权主体不能为空");
            }
            SysGrant grant = new SysGrant();
            grant.setSubjectType(subjectType);
            grant.setSubjectId(subjectId);
            grant.setEffect(StringUtils.defaultIfBlank(
                    StringUtils.upperCase(payload.effect()), SysGrant.EFFECT_ALLOW));
            grant.setActions(JSON.toJSONString(List.of(ACTION)));
            grant.setDataScope(SysGrant.SCOPE_ALL);
            grant.setStatus(SysGrant.STATUS_ACTIVE);
            grants.add(grant);
        }
        return grants;
    }

    private List<GrantItem> items(List<SysGrant> grants, String scope) {
        List<GrantItem> items = new ArrayList<>();
        for (SysGrant grant : grants) {
            items.add(new GrantItem(grant.getId(), grant.getSubjectType(), grant.getSubjectId(),
                    subjectName(grant.getSubjectType(), grant.getSubjectId()), grant.getEffect(),
                    grant.getActions(), grant.getTenantCode(), scope));
        }
        return items;
    }

    private String subjectName(String subjectType, String subjectId) {
        if (StringUtils.isBlank(subjectType) || StringUtils.isBlank(subjectId)) {
            return subjectId;
        }
        return TenantUtil.callAsSystem(() -> switch (subjectType) {
            case "ROLE" -> {
                Role role = roleDao.queryById(subjectId);
                yield role == null ? subjectId : role.getRoleName();
            }
            case "USER" -> {
                User user = userDao.getByEmployeeId(subjectId);
                yield user == null ? subjectId : user.getRealName();
            }
            case "POSITION" -> {
                SysPosition position = positionDao.queryById(subjectId);
                yield position == null ? subjectId : position.getPositionName();
            }
            default -> subjectId;
        });
    }

    private int subjects(List<SysGrant> grants) {
        Set<String> subjects = new LinkedHashSet<>();
        for (SysGrant grant : grants) {
            if (grant.isAllow()) {
                subjects.add(grant.getSubjectType() + ":" + grant.getSubjectId());
            }
        }
        return subjects.size();
    }

    private static String defaultMode(String accessMode) {
        return StringUtils.isBlank(accessMode)
                ? AiPermissionService.MODE_RESTRICTED
                : accessMode.toUpperCase(Locale.ROOT);
    }
}
