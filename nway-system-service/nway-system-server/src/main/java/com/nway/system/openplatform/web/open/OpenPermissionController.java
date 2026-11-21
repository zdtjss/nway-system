package com.nway.system.openplatform.web.open;

import com.nway.system.authorization.service.decision.AuthzBundleService;
import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.authorization.domain.MergedPolicy;
import com.nway.system.common.web.Result;
import com.nway.system.openplatform.web.context.OpenAppContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开放平台：权限策略查询 API。
 * <p>
 * 供子应用通过应用 Token 鉴权后调用，获取用户在本应用的权限策略。
 * <p>
 * 应用身份取自开放平台 Token（appCode），只能查询自身应用的策略，
 * 不信任任何请求头或请求参数中的应用标识。
 */
@RestController
@RequestMapping("open/permission")
public class OpenPermissionController {

    private static final Logger log = LoggerFactory.getLogger(OpenPermissionController.class);

    /** 弃用告警按 appCode 每小时最多一条：既要能定位调用方，又不能刷屏。 */
    private static final long DEPRECATED_WARN_INTERVAL_MILLIS = 3_600_000L;
    private final Map<String, Long> deprecatedWarnedAt = new ConcurrentHashMap<>();

    @Autowired
    private PolicyService policyService;

    @Autowired
    private AuthzBundleService authzBundleService;

    /**
     * 拉取用户在本应用（Token appCode）下的策略包：版本 + 资源动作 + 数据谓词。
     * <p>
     * 外部 SDK 按版本缓存；ETag 等于版本，{@code If-None-Match} 命中同版本时返回 304，
     * 避免每次 TTL 过期都重新传输并解析整包。
     */
    @GetMapping("bundle")
    public org.springframework.http.ResponseEntity<Result<AuthzBundleService.Bundle>> bundle(
            @RequestParam String employeeId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        String appCode = currentAppCode();
        if (StringUtils.isBlank(appCode)) {
            return org.springframework.http.ResponseEntity.ok(
                    Result.ok(new AuthzBundleService.Bundle(0, null, List.of())));
        }
        AuthzBundleService.Bundle bundle = authzBundleService.bundle(employeeId, appCode);
        String etag = String.valueOf(bundle.version());
        var builder = org.springframework.http.ResponseEntity.ok()
                .eTag(etag);
        if (matchesEtag(ifNoneMatch, etag)) {
            return org.springframework.http.ResponseEntity.status(304).eTag(etag).build();
        }
        return builder.body(Result.ok(bundle));
    }

    /** 比较 If-None-Match 与当前版本（兼容强/弱验证器与引号差异）。 */
    private static boolean matchesEtag(String ifNoneMatch, String etag) {
        if (StringUtils.isBlank(ifNoneMatch)) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String normalized = candidate.trim();
            if (normalized.startsWith("W/")) {
                normalized = normalized.substring(2);
            }
            normalized = StringUtils.strip(normalized, "\"");
            if ("*".equals(normalized) || etag.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询用户在当前应用（Token appCode）下的权限策略列表。
     *
     * @param employeeId 用户工号
     * @deprecated 改用 {@code GET /open/permission/bundle}（已判定结果 + 版本号 + ETag）。
     *         本接口保留一个版本用于存量 SDK（{@code PermissionPolicyClient}）迁移，
     *         之后删除；响应带 {@code Deprecation: true} 与后继接口 Link，调用方可据此告警。
     */
    @Deprecated
    @GetMapping("policies")
    public org.springframework.http.ResponseEntity<Result<List<MergedPolicy>>> queryPolicies(
            @RequestParam String employeeId) {
        String appCode = currentAppCode();
        if (StringUtils.isBlank(appCode)) {
            // fail-closed：无法识别应用身份时不返回任何策略
            return org.springframework.http.ResponseEntity.ok(Result.ok(List.of()));
        }
        warnDeprecatedCaller(appCode, employeeId);
        List<MergedPolicy> policies = policyService.getMergedPoliciesByApp(employeeId, appCode);
        return org.springframework.http.ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Link", "</open/permission/bundle>; rel=\"successor-version\"")
                .body(Result.ok(policies));
    }

    /** 记录谁还在调旧接口（按 appCode 限流），便于下线前确认调用方已全部迁移。 */
    private void warnDeprecatedCaller(String appCode, String employeeId) {
        long now = System.currentTimeMillis();
        Long last = deprecatedWarnedAt.get(appCode);
        if (last == null || now - last >= DEPRECATED_WARN_INTERVAL_MILLIS) {
            deprecatedWarnedAt.put(appCode, now);
            log.warn("开放接口 /open/permission/policies 已弃用：调用方 appCode={} employeeId={}；"
                    + "请改用 /open/permission/bundle（下一版本删除）", appCode, employeeId);
        }
    }

    /**
     * 检查用户是否有指定操作权限（快捷接口）。
     *
     * @param employeeId   用户工号
     * @param resourceCode 资源标识
     * @param action       操作标识
     */
    @GetMapping("check")
    public Result<Boolean> checkPermission(@RequestParam String employeeId,
                                           @RequestParam String resourceCode,
                                           @RequestParam(required = false) String action) {
        String appCode = currentAppCode();
        if (StringUtils.isBlank(appCode)) {
            // fail-closed：无法识别应用身份时视为无权限
            return Result.ok(false);
        }
        Map<String, MergedPolicy> policies = policyService.getMergedPolicies(employeeId);
        MergedPolicy policy = policies.get(appCode + ":" + resourceCode);
        if (policy == null) {
            policy = policies.get(appCode + ":*");
        }
        if (policy == null) {
            return Result.ok(false);
        }
        if (StringUtils.isBlank(action)) {
            return Result.ok(true);
        }
        return Result.ok(policy.hasAction(action));
    }

    /**
     * 从开放平台 Token 上下文获取当前应用编码。
     */
    private String currentAppCode() {
        OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
        if (appInfo == null || StringUtils.isBlank(appInfo.getAppCode())) {
            return null;
        }
        return appInfo.getAppCode();
    }
}
