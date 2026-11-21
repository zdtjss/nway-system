package com.nway.system.authorization.service.decision;

import com.alibaba.fastjson2.JSON;
import com.nway.system.authorization.dao.GrantDao;
import com.nway.system.authorization.entity.SysGrant;
import com.nway.system.identity.dao.SysUserPositionDao;
import com.nway.system.identity.entity.SysUserPosition;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.UserRole;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 授权变更预演：影响多少人、每个资源上动作的增减。
 * <p>
 * 不写库；用于管理端“保存前预览”。
 */
@Service
public class AuthzPreviewService {

    private final GrantDao grantDao;
    private final UserRoleDao userRoleDao;
    private final SysUserPositionDao userPositionDao;
    private final UserDao userDao;

    public AuthzPreviewService(GrantDao grantDao, UserRoleDao userRoleDao,
                               SysUserPositionDao userPositionDao, UserDao userDao) {
        this.grantDao = grantDao;
        this.userRoleDao = userRoleDao;
        this.userPositionDao = userPositionDao;
        this.userDao = userDao;
    }

    public PreviewResult preview(String subjectType, String subjectId, List<SysGrant> proposed) {
        List<String> users = affectedUsers(subjectType, subjectId);
        List<String> sample = users.stream().limit(10).toList();
        Map<String, Set<String>> currentAllow = new LinkedHashMap<>();
        Map<String, Set<String>> currentDeny = new LinkedHashMap<>();
        for (SysGrant grant : grantDao.queryBySubject(subjectType, subjectId)) {
            collect(grant, grant.isAllow() ? currentAllow : currentDeny);
        }
        Map<String, Set<String>> nextAllow = new LinkedHashMap<>();
        Map<String, Set<String>> nextDeny = new LinkedHashMap<>();
        if (proposed != null) {
            for (SysGrant grant : proposed) {
                collect(grant, SysGrant.EFFECT_DENY.equalsIgnoreCase(grant.getEffect()) ? nextDeny : nextAllow);
            }
        }
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(currentAllow.keySet());
        keys.addAll(currentDeny.keySet());
        keys.addAll(nextAllow.keySet());
        keys.addAll(nextDeny.keySet());
        List<DiffItem> diffs = new ArrayList<>();
        for (String key : keys) {
            Set<String> added = minus(nextAllow.get(key), currentAllow.get(key));
            Set<String> removed = minus(currentAllow.get(key), nextAllow.get(key));
            Set<String> denied = minus(nextDeny.get(key), currentDeny.get(key));
            if (added.isEmpty() && removed.isEmpty() && denied.isEmpty()) {
                continue;
            }
            String[] parts = key.split(":", 2);
            diffs.add(new DiffItem(parts[0], parts[1],
                    List.copyOf(added), List.copyOf(removed), List.copyOf(denied)));
        }
        return new PreviewResult(users.size(), sample, diffs);
    }

    /** 授权主体的影响用户（去重前的原始集合，供 AI 接口权限等场景复用）。 */
    public List<String> affectedUsers(String subjectType, String subjectId) {
        if (subjectType == null) {
            return List.of();
        }
        return switch (subjectType.toUpperCase()) {
            case "ROLE" -> userRoleDao.queryByRole(List.of(subjectId)).stream()
                    .map(UserRole::getEmployeeId).distinct().toList();
            case "USER" -> {
                User user = userDao.getByEmployeeId(subjectId);
                yield user == null ? List.of() : List.of(user.getEmployeeId());
            }
            case "POSITION" -> userPositionDao.queryByPosition(subjectId).stream()
                    .map(SysUserPosition::getEmployeeId).distinct().toList();
            default -> List.of();
        };
    }

    private static void collect(SysGrant grant, Map<String, Set<String>> target) {
        Set<String> actions = parse(grant.getActions());
        target.computeIfAbsent(grant.getAppCode() + ":" + grant.getResourceCode(),
                k -> new LinkedHashSet<>()).addAll(actions);
    }

    private static Set<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = JSON.parseArray(json, String.class);
            return list == null ? Set.of() : new LinkedHashSet<>(list);
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private static Set<String> minus(Set<String> left, Set<String> right) {
        if (left == null || left.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>(left);
        if (right != null) {
            result.removeAll(right);
        }
        return result;
    }

    public record PreviewResult(int affectedUsers, List<String> sampleUsers, List<DiffItem> diffs) {
    }

    public record DiffItem(String appCode, String resourceCode,
                           List<String> added, List<String> removed, List<String> denied) {
    }
}
