package com.nway.system.openplatform.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.authorization.service.policy.PolicyService;
import com.nway.system.config.properties.JwtProperties;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.JwtUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.openplatform.entity.SysOpenApp;
import com.nway.system.authorization.entity.PermissionPolicy;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 开放平台 Token 服务。
 * <p>
 * 支持三种授权模式：
 * <ul>
 *   <li>client_credentials — 应用自身身份，无用户上下文</li>
 *   <li>user_delegate — 代理指定用户，需用户提供有效 JWT</li>
 *   <li>authorization_code — SSO 授权码模式，通过 OpenSsoService 消费</li>
 * </ul>
 * <p>
 * <b>Token 租户语义</b>：{@code tenantCode} 声明是<b>数据作用域租户</b>，而非"凭据应用所属租户"。
 * user_delegate 模式可由 {@code targetTenantCode} 指定（须 ∈ 代理用户可管理租户，见
 * {@link OpenTenantScopeResolver}）；缺省=凭据应用所属租户（与改造前逐项一致）。
 * 开放端 {@code /open/**} 的 ORM 数据访问据此收敛（OpenApiInterceptor → TenantPermissionStrategy）。
 * <p>
 * <b>凭据应用的租户边界</b>：默认要求"凭据应用租户 ∈ 代理用户可管理租户"；唯一例外是
 * 注册在 public（公共租户）的<b>平台级集成应用</b>——显式指定作用域租户时可代理任意租户
 * （作用域仍受"targetTenantCode ∈ 可管理租户"约束，数据隔离不弱化）。
 *
 * @author nway
 */
@Slf4j
@Service
public class OpenTokenService {

    /** 通配 scope：策略资源为 {@code *} 时派生，拦截器据此外放行任意 scope。 */
    private static final String WILDCARD = "*";

    @Autowired
    private OpenAppService openAppService;

    @Autowired
    private OpenSsoService openSsoService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private OpenTenantScopeResolver openTenantScopeResolver;

    @Autowired
    private PolicyService policyService;

    /**
     * 签发 Token（三种模式统一入口）。
     *
     * @param appCode          应用编码
     * @param appSecret        应用密钥（明文）
     * @param grantType        授权模式：client_credentials / user_delegate / authorization_code
     * @param userToken        用户 JWT（user_delegate 模式必填）
     * @param authCode         授权码（authorization_code 模式必填）
     * @param targetTenantCode 数据作用域租户（可空，仅 user_delegate 生效；须 ∈ 代理用户可管理租户，
     *                         缺省=凭据应用所属租户，与改造前语义逐项一致）
     * @return Token 响应（access_token, expires_in, scope, employeeId 等）
     */
    public Map<String, Object> issueToken(String appCode, String appSecret, String grantType,
                                           String userToken, String authCode, String targetTenantCode) {
        // 1. 查应用
        // /open/token 在拦截器中早退、无租户上下文；应用可能注册在任意租户，按编码全局查询 → 显式系统上下文
        SysOpenApp app = TenantUtil.callAsSystem(() -> openAppService.getByCode(appCode));
        if (app == null) {
            throw new NwaySystemException("应用不存在: " + appCode);
        }
        if (!"using".equals(app.getStatus())) {
            throw new NwaySystemException("应用已禁用");
        }
        // 检查过期
        if (app.getExpireTime() != null && app.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
            throw new NwaySystemException("应用已过期");
        }

        // 2. 验证密钥
        if (!openAppService.verifySecret(app, appSecret)) {
            throw new NwaySystemException("密钥错误");
        }

        // 3. 解析已授权 scope：应用表存量配置 ∪ APP 主体策略派生的 scope
        List<String> grantedScopes = TenantUtil.callAsSystem(() -> resolveGrantedScopes(app));

        // 4. 按模式签发
        String employeeId = null;
        switch (grantType) {
            case "client_credentials":
                // 无用户上下文
                break;
            case "user_delegate":
                if (!StringUtils.hasText(userToken)) {
                    throw new NwaySystemException("user_delegate 模式需要 userToken");
                }
                Claims userClaims = JwtUtil.parseToken(userToken, jwtProperties.getSecret());
                if (userClaims == null) {
                    throw new NwaySystemException("用户 Token 无效或已过期");
                }
                employeeId = JwtUtil.getEmployeeId(userClaims);
                break;
            case "authorization_code":
                if (!StringUtils.hasText(authCode)) {
                    throw new NwaySystemException("authorization_code 模式需要 code");
                }
                OpenSsoService.AuthCodeInfo codeInfo = openSsoService.consumeAuthCode(authCode);
                if (codeInfo == null) {
                    throw new NwaySystemException("授权码无效或已过期");
                }
                if (!appCode.equals(codeInfo.getAppCode())) {
                    throw new NwaySystemException("授权码与应用不匹配");
                }
                employeeId = codeInfo.getEmployeeId();
                break;
            default:
                throw new NwaySystemException("不支持的授权模式: " + grantType);
        }

        // 4.1 应用必须归属租户
        if (!StringUtils.hasText(app.getTenantCode())) {
            throw new NwaySystemException("应用未归属租户，禁止签发开放 Token");
        }
        // 4.2 代理授权边界：
        //   - 显式作用域（targetTenantCode）须 ∈ 代理用户可管理租户——数据隔离的真正边界，恒不放宽；
        //   - 凭据应用租户须 ∈ 代理用户可管理租户；唯一例外：注册在 public（公共租户）的平台级
        //     集成应用，显式指定作用域时可代理任意租户（如低代码平台用一份公共凭据为各租户同步菜单）
        String scopeTenantCode = app.getTenantCode();
        if (StringUtils.hasText(employeeId)) {
            boolean explicitTarget = StringUtils.hasText(targetTenantCode)
                    && !targetTenantCode.equals(app.getTenantCode());
            boolean publicIntegrationApp = TenantUtil.PUBLIC_TENANT_CODE.equals(app.getTenantCode());
            if ((!explicitTarget || !publicIntegrationApp)
                    && !canManage(employeeId, app.getTenantCode())) {
                throw new NwaySystemException("代理用户与应用不属于可管理范围，禁止代理授权");
            }
            if (explicitTarget) {
                if (!canManage(employeeId, targetTenantCode)) {
                    throw new NwaySystemException("无权以该租户为数据作用域签发 Token: " + targetTenantCode);
                }
                scopeTenantCode = targetTenantCode;
            }
        } else if (StringUtils.hasText(targetTenantCode) && !targetTenantCode.equals(app.getTenantCode())) {
            // client_credentials / authorization_code：作用域固定为应用所属租户，不接受指定
            throw new NwaySystemException("仅 user_delegate 模式支持指定数据作用域租户");
        }

        // 5. 签发开放平台 Token（使用独立密钥，与用户 Token 隔离）
        //    tenantCode = 数据作用域租户（跨租户代办时 ≠ 凭据应用租户），开放端 ORM 隔离收敛到该租户
        List<String> dataScopeDeptIds = parseDeptIds(app.getDataScopeDeptIds());
        String token = JwtUtil.generateOpenAppToken(
                appCode,
                scopeTenantCode,
                grantedScopes,
                app.getDataScopeType(),
                dataScopeDeptIds,
                employeeId,
                jwtProperties.getEffectiveOpenSecret()
        );

        // 6. 构建响应
        Map<String, Object> result = new HashMap<>();
        result.put("access_token", token);
        result.put("token_type", "Bearer");
        result.put("expires_in", JwtUtil.OPEN_APP_EXPIRE_MILLIS / 1000);
        result.put("scope", String.join(" ", grantedScopes));
        if (employeeId != null) {
            result.put("employee_id", employeeId);
        }
        return result;
    }

    /**
     * 代理用户是否可管理目标租户（/open/token 无租户上下文，显式声明系统上下文后查询）。
     */
    private boolean canManage(String employeeId, String tenantCode) {
        return TenantUtil.callAsSystem(() -> openTenantScopeResolver.canManage(employeeId, tenantCode));
    }

    /**
     * 验证开放平台 Token 并返回解析后的 Claims。
     *
     * @param token 开放平台 JWT
     * @return Claims；无效返回 null
     */
    public Claims verifyToken(String token) {
        Claims claims = JwtUtil.parseToken(token, jwtProperties.getEffectiveOpenSecret());
        if (claims == null) {
            return null;
        }
        if (!JwtUtil.isOpenToken(claims)) {
            log.warn("非开放平台 Token 被用于开放平台接口");
            return null;
        }
        return claims;
    }

    private List<String> parseScopes(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, String.class);
    }

    /**
     * 组装开放 Token 的 grantedScopes —— 统一权限模型的接入点。
     * <p>
     * 两部分<b>并集</b>（兼容期设计，见 权限模型重构技术方案 §4.1.4）：
     * <ol>
     *   <li>应用表存量 {@code granted_scopes}：保持既有配置零影响；</li>
     *   <li>APP 主体策略派生：{@code sys_permission_policy} 中
     *       {@code subject_type=APP, subject_id=应用编码} 的策略行，
     *       由 {@code resource_code:action} 组合成 scope 字符串。</li>
     * </ol>
     * 未配置 APP 策略时结果与改造前逐项一致（零行为变更）；配置后为<b>增量</b>授权，
     * 不会剥夺存量 scope。
     * <p>
     * 注：scope 在签发时固化进 Token，有效期 2 小时。修改 APP 策略对<b>已签发</b>的 Token
     * 不生效（自然过期即收敛），这是有意为之——避免每次开放接口调用都回查策略。
     */
    private List<String> resolveGrantedScopes(SysOpenApp app) {
        List<String> legacyScopes = parseScopes(app.getGrantedScopes());
        Set<String> scopes = new LinkedHashSet<>(legacyScopes);
        List<PermissionPolicy> appPolicies = policyService.queryByAppSubject(app.getAppCode());
        for (PermissionPolicy policy : appPolicies) {
            scopes.addAll(toScopes(policy));
        }
        if (!appPolicies.isEmpty()) {
            log.info("开放 Token scope 组装: appCode={}, 存量 {} 项 + 策略 {} 条 → 合计 {} 项",
                    app.getAppCode(), legacyScopes.size(), appPolicies.size(), scopes.size());
        }
        return new ArrayList<>(scopes);
    }

    /**
     * APP 策略 → scope 字符串集合。
     * <p>
     * 规则：{@code resource_code:action}；资源为 {@code *} 时派生通配 scope {@code *}
     * （拦截器已支持 {@code grantedScopes.contains("*")} 放行任意 scope）。
     * {@code actions} 为 null 表示「全部操作」（与策略表语义一致），展开为该资源的基础读写 scope；
     * 显式空数组 {@code []} 表示不授予任何 scope。
     * <p>
     * 注意：特殊 scope（如 {@code msg:send}、{@code dp:check}）需要在 actions 中显式列出
     * （写 {@code send} / {@code check}），不会被「全部操作」自动展开。
     */
    private List<String> toScopes(PermissionPolicy policy) {
        String resource = policy.getResourceCode();
        if (!StringUtils.hasText(resource)) {
            return Collections.emptyList();
        }
        if (WILDCARD.equals(resource)) {
            return List.of(WILDCARD);
        }
        if (policy.getActions() == null) {
            return List.of(resource + ":read", resource + ":write");
        }
        List<String> actions = JSON.parseArray(policy.getActions(), String.class);
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> scopes = new ArrayList<>(actions.size());
        for (String action : actions) {
            if (StringUtils.hasText(action)) {
                scopes.add(resource + ":" + action);
            }
        }
        return scopes;
    }

    private List<String> parseDeptIds(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, String.class);
    }
}
