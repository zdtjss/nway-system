package com.nway.system.identity.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.nway.system.authorization.service.policy.PolicyCacheManager;
import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.JwtUtil;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.config.properties.JwtProperties;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.domain.auth.AuthorizationService;
import com.nway.system.identity.domain.auth.AuthorizationServiceRegistry;
import com.nway.system.identity.domain.auth.external.ExternalPasswordAuthenticator;
import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.tenant.support.TenantStatusCache;
import com.nway.system.common.util.ClientIpResolver;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 登录服务（JWT 无状态认证）。
 * <p>
 * 登录成功后签发 JWT，后续请求通过本地验签鉴权，不依赖 Redis 存 Token。
 * 强制下线通过 {@link UserStatusCache} 实现（内存黑名单，30 秒内生效）。
 *
 * @author nway
 */
@Slf4j
@Service
public class LoginService {

    @Autowired
    private AuthorizationServiceRegistry authorizationServiceRegistry;

    @Autowired
    private PolicyCacheManager policyCacheManager;

    @Autowired
    private UserStatusCache userStatusCache;

    @Autowired
    private TenantStatusCache tenantStatusCache;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserService userService;

    @Autowired
    private UserLoginKeyService loginKeyService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private LoginSecurityService loginSecurityService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private TokenVersionPubSubService tokenVersionPubSubService;

    @Autowired
    private ExternalPasswordAuthenticator externalPasswordAuthenticator;

    /**
     * 登录验证，成功后签发 JWT。
     * <p>
     * 登录安全流程：
     * <ol>
     *   <li>IP 级限流：同一 IP 60 秒内最多 10 次尝试</li>
     *   <li>临时锁定检查：连续失败 5 次后临时锁定 30 分钟，自动恢复</li>
     *   <li>永久禁用检查：仅管理员手动操作触发</li>
     *   <li>认证失败时记录失败次数，达到阈值触发临时锁定</li>
     *   <li>登录成功时清除失败记录</li>
     * </ol>
     *
     * @param loginDTO 登录信息
     * @return 含 JWT Token 的 LoginUser
     */
    public LoginUser authenticate(LoginDto loginDTO) {
        // 密码登录才有用户名；bio 登录 name 为空，锁定/禁用检查由认证通过后按实际用户执行
        String employeeId = loginDTO.getName();
        String clientIp = resolveClientIp();

        // Layer 1: IP 级限流
        if (loginSecurityService.isIpBlocked(clientIp)) {
            throw new NwaySystemException(ErrorCodeEnum.LOGIN_FAIL_LIMIT);
        }

        // Layer 2: 临时锁定检查（自动过期，无需管理员干预）
        if (employeeId != null && loginSecurityService.isLocked(employeeId)) {
            throw new NwaySystemException(ErrorCodeEnum.LOGIN_FAIL_LIMIT);
        }

        // Layer 3: 永久禁用检查（仅管理员手动操作触发）
        if (employeeId != null
                && (userStatusCache.isDisabled(employeeId)
                    || TenantUtil.callAsSystem(() -> userService.isUserDisabled(employeeId)))) {
            throw new NwaySystemException(ErrorCodeEnum.ACCOUNT_LOCKED);
        }
        AuthorizationService authorizationService = authorizationServiceRegistry.get(loginDTO.getLoginType());
        LoginUser loginUser;
        try {
            loginUser = authorizationService.authenticate(loginDTO);
        } catch (NwaySystemException e) {
            // 登录失败，记录失败次数
            handleLoginFailure(employeeId);
            throw e;
        }
        if (loginUser == null) {
            // 认证返回 null，视为失败
            handleLoginFailure(employeeId);
            throw new NwaySystemException("登录失败");
        }
        // 租户生命周期：所属租户被禁用/到期则拒绝登录（用户状态之外的第二道边界）
        if (!loginUser.isSuperAdmin() && !tenantStatusCache.isUsable(loginUser.getTenantCode())) {
            throw new NwaySystemException("租户已禁用或已到期，无法登录");
        }
        // 登录成功，清除失败记录
        loginSecurityService.clearFailure(loginUser.getEmployeeId());
        // 登录时清除旧的权限缓存
        LoginUserContext.set(loginUser);
        try {
            policyCacheManager.evict(loginUser.getEmployeeId());
        } finally {
            LoginUserContext.clean();
        }

        // 签发 JWT（含 token 版本号 + sessionId + 需改密标记）
        // 登录为匿名路径，此刻无登录上下文，跨租户读取须显式声明系统上下文
        int tokenVersion = TenantUtil.callAsSystem(() -> userDao.getTokenVersion(loginUser.getEmployeeId()));
        String sessionId = JwtUtil.generateSessionId();
        boolean pwdResetRequired = TenantUtil.callAsSystem(
                () -> loginKeyService.isPwdResetRequired(loginUser.getEmployeeId()));
        String token = JwtUtil.generateToken(
                loginUser.getEmployeeId(),
                loginUser.getTenantCode(),
                loginUser.getRealName(),
                loginUser.getMainDeptCode(),
                loginUser.isSuperAdmin(),
                loginUser.getRoleList() != null ? loginUser.getRoleList() : List.of(),
                jwtProperties.getSecret(),
                jwtProperties.getExpireMillis(),
                tokenVersion,
                0,
                sessionId,
                pwdResetRequired
        );
        loginUser.setToken(token);
        loginUser.setTokenVersion(tokenVersion);
        loginUser.setPwdResetRequired(pwdResetRequired);
        // 下发续期阈值：前端据此判断何时调用 /login/refresh，避免前后端阈值不一致
        loginUser.setRefreshThresholdMillis(jwtProperties.getRefreshThresholdMillis());
        // 缓存 token 版本号
        userStatusCache.setTokenVersion(loginUser.getEmployeeId(), tokenVersion);
        // 记录在线用户会话到 Redis（IP/User-Agent 取自当前请求）
        try {
            HttpServletRequest request = currentRequest();
            String ip = request != null ? getClientIp(request) : null;
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            onlineUserService.recordLogin(
                    loginUser.getEmployeeId(), sessionId, ip, userAgent, jwtProperties.getExpireMillis());
        } catch (Exception e) {
            log.warn("记录在线用户会话失败 employeeId={}", loginUser.getEmployeeId(), e);
        }
        log.info("{} 登录成功", loginUser.getEmployeeId());
        return loginUser;
    }

    /**
     * 处理登录失败：记录失败次数和 IP 尝试，达到阈值（5 次）触发临时锁定。
     * <p>
     * 临时锁定 30 分钟后自动解锁，不再自动触发永久禁用（永久禁用仅由管理员手动操作）。
     */
    private void handleLoginFailure(String employeeId) {
        // 记录 IP 尝试
        String clientIp = resolveClientIp();
        loginSecurityService.recordIpAttempt(clientIp);

        // bio 登录认证失败时不知道具体用户，无法按用户计数
        if (employeeId == null) {
            return;
        }

        int count = loginSecurityService.recordFailure(employeeId);
        if (count >= loginSecurityService.getMaxAttempts()) {
            // 达到阈值，临时锁定（自动过期恢复）
            loginSecurityService.tempLock(employeeId);
            throw new NwaySystemException(ErrorCodeEnum.LOGIN_FAIL_LIMIT);
        }
    }

    /**
     * 从当前请求上下文中解析客户端 IP。
     */
    private String resolveClientIp() {
        try {
            return clientIpResolver.resolve(currentRequest());
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 外部身份认证账号自助改密（AD）。
     * <p>
     * 登录前密码已过期（外部返回 1014）时使用：凭旧密码证明身份后修改新密码，
     * 不要求已登录，因此由调用方配置为匿名接口。
     *
     * @param username    工号
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    public void changeExternalPassword(String username, String oldPassword, String newPassword) {
        if (StringUtils.isAnyBlank(username, oldPassword, newPassword)) {
            throw new NwaySystemException(400, "工号、原密码和新密码不能为空");
        }
        externalPasswordAuthenticator.changePassword(username.trim().toLowerCase(), oldPassword, newPassword);
    }

    /**
     * 登出。JWT 无状态，前端清除 Token 即可。
     * 后端清除权限缓存 + 移除当前会话的在线记录。
     */
    public boolean logout(String token) {
        Claims claims = JwtUtil.parseToken(token, jwtProperties.getSecret());
        if (claims != null) {
            String employeeId = JwtUtil.getEmployeeId(claims);
            String sessionId = JwtUtil.getSessionId(claims);
            policyCacheManager.evict(employeeId);
            onlineUserService.removeLogin(employeeId, sessionId);
        }
        return true;
    }

    /**
     * Token 续期：校验旧 JWT 仍有效（未过期 + 剩余不足阈值时间），签发新 JWT。
     * <p>
     * 安全限制：
     * <ul>
     *   <li>已过期或无效的 Token 不可续期</li>
     *   <li>被禁用的用户不可续期</li>
     *   <li>Token 版本号已被撤销时不可续期</li>
     *   <li>达到最大续期次数后不可续期（防止泄露 Token 被无限续期）</li>
     * </ul>
     *
     * @param oldToken 当前 JWT
     * @return 新 JWT；不满足续期条件返回 null
     */
    public String refreshToken(String oldToken) {
        Claims claims = JwtUtil.parseToken(oldToken, jwtProperties.getSecret());
        if (claims == null) {
            // 已过期或无效，不能续期
            return null;
        }
        // 检查用户是否被禁用
        String employeeId = JwtUtil.getEmployeeId(claims);
        if (userStatusCache.isDisabled(employeeId)) {
            return null;
        }
        // 校验 Token 版本号（强制下线后旧 Token 不可续期）
        int jwtVersion = JwtUtil.getTokenVersion(claims);
        if (!checkTokenVersion(employeeId, jwtVersion)) {
            return null;
        }
        // 校验续期次数限制（防止泄露 Token 被无限续期）
        int currentRefreshCount = JwtUtil.getRefreshCount(claims);
        if (currentRefreshCount >= jwtProperties.getMaxRefreshCount()) {
            log.info("Token 续期次数已达上限 employeeId={}, count={}", employeeId, currentRefreshCount);
            return null;
        }
        // 判断是否满足续期条件（剩余不足阈值时间）
        if (!JwtUtil.canRefresh(claims, jwtProperties.getRefreshThresholdMillis())) {
            // 不需要续期，返回原 token
            return oldToken;
        }
        // 签发新 JWT（携带当前版本号 + 续期计数 +1 + 保持原 sessionId）
        int currentVersion = TenantUtil.callAsSystem(() -> userDao.getTokenVersion(employeeId));
        return JwtUtil.generateToken(
                employeeId,
                JwtUtil.getTenantCode(claims),
                JwtUtil.getName(claims),
                JwtUtil.getDeptCode(claims),
                JwtUtil.isSuperAdmin(claims),
                JwtUtil.getRoles(claims),
                jwtProperties.getSecret(),
                jwtProperties.getExpireMillis(),
                currentVersion,
                currentRefreshCount + 1,
                JwtUtil.getSessionId(claims),
                JwtUtil.isPwdResetRequired(claims)
        );
    }

    /**
     * 根据 JWT 获取登录用户信息。
     *
     * @param token JWT Token
     * @return LoginUser，无效返回 null
     */
    public LoginUser getLoginUser(String token) {
        Claims claims = JwtUtil.parseToken(token, jwtProperties.getSecret());
        if (claims == null) {
            return null;
        }
        // 开放平台 Token 不得作为用户 Token 使用
        if (JwtUtil.isOpenToken(claims)) {
            return null;
        }
        String employeeId = JwtUtil.getEmployeeId(claims);
        // 校验用户状态
        if (userStatusCache.isDisabled(employeeId)) {
            return null;
        }
        // 从 DB 加载完整用户信息（含部门、角色等）；匿名路径无上下文，显式声明系统上下文
        LoginUser loginUser = TenantUtil.callAsSystem(() -> userService.getByEmployeeId(employeeId));
        if (loginUser != null) {
            loginUser.setPwdResetRequired(JwtUtil.isPwdResetRequired(claims));
        }
        return loginUser;
    }

    /**
     * 从 JWT 快速解析出基本登录信息（不查 DB，用于拦截器高频调用）。
     *
     * @param token JWT Token
     * @return LoginUser（仅含 JWT 载荷信息），无效返回 null
     */
    public LoginUser parseLoginUserFromJwt(String token) {
        Claims claims = JwtUtil.parseToken(token, jwtProperties.getSecret());
        if (claims == null) {
            return null;
        }
        // 开放平台 Token 不得作为用户 Token 使用（两套 Token 的 subject 语义不同）
        if (JwtUtil.isOpenToken(claims)) {
            log.warn("开放平台 Token 被用于平台接口，已拒绝");
            return null;
        }
        String employeeId = JwtUtil.getEmployeeId(claims);
        // 校验用户状态
        if (userStatusCache.isDisabled(employeeId)) {
            return null;
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setEmployeeId(employeeId);
        loginUser.setTenantCode(JwtUtil.getTenantCode(claims));
        loginUser.setSuperAdmin(JwtUtil.isSuperAdmin(claims));
        loginUser.setRoleList(JwtUtil.getRoles(claims));
        loginUser.setTokenVersion(JwtUtil.getTokenVersion(claims));
        loginUser.setSessionId(JwtUtil.getSessionId(claims));
        loginUser.setPwdResetRequired(JwtUtil.isPwdResetRequired(claims));
        loginUser.setToken(token);
        return loginUser;
    }

    /**
     * 校验 Token 版本号是否有效（强制下线/改密码后旧 Token 失效）。
     * <p>
     * 优先从缓存获取版本号，缓存未命中则从 DB 加载并回填缓存。
     *
     * @param employeeId   工号
     * @param jwtVersion   JWT 中携带的版本号
     * @return true 版本号一致（有效），false 版本号过旧（已撤销）
     */
    public boolean checkTokenVersion(String employeeId, int jwtVersion) {
        int currentVersion = userStatusCache.getTokenVersion(employeeId);
        if (currentVersion == -1) {
            // 缓存未命中，从 DB 加载（调用点可能在无上下文的拦截器/续期路径，显式声明系统上下文）
            currentVersion = TenantUtil.callAsSystem(() -> userDao.getTokenVersion(employeeId));
            userStatusCache.setTokenVersion(employeeId, currentVersion);
        }
        // 严格相等：版本号只应等于服务端当前值，过期或超前均视为无效
        return jwtVersion == currentVersion;
    }

    /**
     * 强制使用户所有已签发 Token 失效（自增 tokenVersion）。
     * <p>
     * 用于强制下线、修改密码等场景。
     * 通过 Redis Pub/Sub 广播版本号变更，各实例立即刷新本地缓存。
     *
     * @param employeeId 工号
     */
    public void invalidateTokensByVersion(String employeeId) {
        userDao.incrementTokenVersion(employeeId);
        userStatusCache.evictTokenVersion(employeeId);
        tokenVersionPubSubService.publishVersionChange(employeeId, TokenVersionPubSubService.ACTION_LOGOUT);
    }

    /**
     * 验证 Token 是否有效。
     */
    public Boolean validateToken(String token) {
        Claims claims = JwtUtil.parseToken(token, jwtProperties.getSecret());
        if (claims == null) {
            return false;
        }
        String employeeId = JwtUtil.getEmployeeId(claims);
        return !userStatusCache.isDisabled(employeeId);
    }

    /**
     * 获取当前线程绑定的 HttpServletRequest，无请求上下文时返回 null。
     */
    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析客户端真实 IP，依次识别 X-Forwarded-For / X-Real-IP / Proxy-Client-IP / RemoteAddr。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
