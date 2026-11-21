package com.nway.system.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具类。
 * <p>
 * 签发与验证用户 Token 和开放平台 Token，使用 HMAC-SHA 签名。
 * <p>
 * 用户 Token 声明（契约 C-IDENT v0.2）：
 * {@code iss/aud/jti/sub/tenantCode/iat/exp} 为必填，{@code name/deptCode/roles} 为可选；
 * {@code ver/rc/sid/pr/superAdmin} 是宿主自身会话状态，仅签发方使用。
 * 不再签发 {@code uid}（宿主主键是 VARCHAR 业务 id，与下游本地 BIGINT 用户 id 不是同一 ID 空间）。
 *
 * @author nway
 */
@Slf4j
public final class JwtUtil {

    private JwtUtil() {
    }

    /** 签发方标识（契约 C-IDENT v0.2）。 */
    public static final String ISSUER = "nway-system";

    /**
     * 用户 Token 的受众：浏览器同时访问 wform、workflow-engine，以及 OA/BFF 主链路。
     * 消费方各自校验自己关心的那一个，从而阻断跨服务回放。
     * <p>
     * 不影响既有 wform / workflow 的受众校验。
     */
    public static final List<String> AUDIENCE = List.of("wform", "workflow", "bff", "eccp");

    /** 默认有效期：24 小时（实际以 nway.jwt.expire-millis 配置为准） */
    public static final long DEFAULT_EXPIRE_MILLIS = 24 * 60 * 60 * 1000L;

    /** 续期阈值：剩余不足 5 分钟可续期 */
    public static final long REFRESH_THRESHOLD_MILLIS = 5 * 60 * 1000L;

    /** 开放平台 Token 有效期：2 小时 */
    public static final long OPEN_APP_EXPIRE_MILLIS = 2 * 60 * 60 * 1000L;

    /**
     * 签发用户 JWT。
     *
     * @param employeeId 工号
     * @param tenantCode 租户编码
     * @param superAdmin 是否超管
     * @param roles      角色编码列表
     * @param secret     签名密钥字符串
     * @return JWT Token
     */
    public static String generateToken(String employeeId, String tenantCode, boolean superAdmin,
                                       List<String> roles, String secret) {
        return generateToken(employeeId, tenantCode, superAdmin, roles, secret, DEFAULT_EXPIRE_MILLIS, 0);
    }

    /**
     * 签发用户 JWT（自定义有效期）。
     */
    public static String generateToken(String employeeId, String tenantCode, boolean superAdmin,
                                       List<String> roles, String secret, long expireMillis) {
        return generateToken(employeeId, tenantCode, superAdmin, roles, secret, expireMillis, 0);
    }

    /**
     * 签发用户 JWT（自定义有效期 + Token 版本号）。
     *
     * @param employeeId   工号
     * @param tenantCode   租户编码
     * @param superAdmin   是否超管
     * @param roles        角色编码列表
     * @param secret       签名密钥字符串
     * @param expireMillis 有效期（毫秒）
     * @param tokenVersion Token 版本号，强制下线时自增
     * @return JWT Token
     */
    public static String generateToken(String employeeId, String tenantCode, boolean superAdmin,
                                       List<String> roles, String secret, long expireMillis, int tokenVersion) {
        return generateToken(employeeId, tenantCode, superAdmin, roles, secret, expireMillis, tokenVersion, 0);
    }

    /**
     * 签发用户 JWT（自定义有效期 + Token 版本号 + 续期计数）。
     *
     * @param employeeId   工号
     * @param tenantCode   租户编码
     * @param superAdmin   是否超管
     * @param roles        角色编码列表
     * @param secret       签名密钥字符串
     * @param expireMillis 有效期（毫秒）
     * @param tokenVersion Token 版本号，强制下线时自增
     * @param refreshCount 已续期次数，每次续期 +1
     * @return JWT Token
     */
    public static String generateToken(String employeeId, String tenantCode, boolean superAdmin,
                                       List<String> roles, String secret, long expireMillis,
                                       int tokenVersion, int refreshCount) {
        return generateToken(employeeId, tenantCode, superAdmin, roles, secret, expireMillis,
                tokenVersion, refreshCount, generateSessionId());
    }

    /**
     * 签发用户 JWT（完整参数，含 sessionId）。
     * <p>
     * 续期时复用原 sessionId；首次登录时生成新 sessionId。
     *
     * @param employeeId   工号
     * @param tenantCode   租户编码
     * @param superAdmin   是否超管
     * @param roles        角色编码列表
     * @param secret       签名密钥字符串
     * @param expireMillis 有效期（毫秒）
     * @param tokenVersion Token 版本号，强制下线时自增
     * @param refreshCount 已续期次数，每次续期 +1
     * @param sessionId    会话标识，同一会话续期时保持不变
     * @return JWT Token
     */
    public static String generateToken(String employeeId, String tenantCode, boolean superAdmin,
                                       List<String> roles, String secret, long expireMillis,
                                       int tokenVersion, int refreshCount, String sessionId) {
        return generateToken(employeeId, tenantCode, superAdmin, roles, secret, expireMillis,
                tokenVersion, refreshCount, sessionId, false);
    }

    /**
     * 签发用户 JWT（完整参数，含 sessionId 与需改密标记）。
     *
     * @param pwdResetRequired 是否需先修改初始密码（为 true 时该 Token 仅可用于改密等登录接口）
     */
    public static String generateToken(String employeeId, String tenantCode, boolean superAdmin,
                                       List<String> roles, String secret, long expireMillis,
                                       int tokenVersion, int refreshCount, String sessionId,
                                       boolean pwdResetRequired) {
        return generateToken(employeeId, tenantCode, null, null, superAdmin, roles, secret, expireMillis,
                tokenVersion, refreshCount, sessionId, pwdResetRequired);
    }

    /**
     * 签发用户 JWT（含姓名/部门声明）。
     * <p>
     * {@code name}/{@code deptCode} 是跨工程契约 C-IDENT 的一部分：workflow-engine 的 realName
     * 与数据权限部门维度都依赖它们，缺失会退化工号／无法解析部门。
     * <p>
     * 契约 v0.2：写入 {@code iss/aud/jti}；不再写入宿主内部主键 {@code uid}。
     */
    public static String generateToken(String employeeId, String tenantCode, String realName, String deptCode,
                                       boolean superAdmin, List<String> roles, String secret, long expireMillis,
                                       int tokenVersion, int refreshCount, String sessionId,
                                       boolean pwdResetRequired) {
        SecretKey key = getSecretKey(secret);
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMillis);

        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .id(UUID.randomUUID().toString())
                .subject(employeeId)
                .claim("tenantCode", tenantCode)
                .claim("superAdmin", superAdmin)
                .claim("roles", roles)
                .claim("ver", tokenVersion)
                .claim("rc", refreshCount)
                .claim("sid", sessionId)
                .claim("pr", pwdResetRequired)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key);
        if (org.apache.commons.lang3.StringUtils.isNotBlank(realName)) {
            builder.claim("name", realName);
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(deptCode)) {
            builder.claim("deptCode", deptCode);
        }
        return builder.compact();
    }

    /**
     * 生成会话标识（16 位无连字符 UUID 前缀，保证唯一性且紧凑）。
     */
    public static String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 解析 JWT，验签 + 校验过期。
     *
     * @param token  JWT Token
     * @param secret 签名密钥字符串
     * @return Claims，验签失败或过期返回 null
     */
    public static Claims parseToken(String token, String secret) {
        try {
            SecretKey key = getSecretKey(secret);
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 JWT（允许过期，用于续期场景）。
     * 验签通过但已过期的 Token 也能解析出 Claims。
     *
     * @return Claims；验签失败返回 null，过期返回正常 Claims
     */
    public static Claims parseTokenAllowExpired(String token, String secret) {
        try {
            SecretKey key = getSecretKey(secret);
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 过期但签名有效，返回 Claims
            return e.getClaims();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断 Token 是否可续期（未过期 + 剩余不足阈值时间）。
     * 使用默认阈值 {@link #REFRESH_THRESHOLD_MILLIS}。
     */
    public static boolean canRefresh(Claims claims) {
        return canRefresh(claims, REFRESH_THRESHOLD_MILLIS);
    }

    /**
     * 判断 Token 是否可续期（未过期 + 剩余不足指定阈值时间）。
     *
     * @param claims                JWT Claims
     * @param refreshThresholdMillis 续期阈值（毫秒）
     * @return true 可续期
     */
    public static boolean canRefresh(Claims claims, long refreshThresholdMillis) {
        if (claims == null || claims.getExpiration() == null) {
            return false;
        }
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        return remaining > 0 && remaining < refreshThresholdMillis;
    }

    /**
     * 判断 Token 是否已过期。
     */
    public static boolean isExpired(Claims claims) {
        if (claims == null || claims.getExpiration() == null) {
            return true;
        }
        return claims.getExpiration().getTime() < System.currentTimeMillis();
    }

    /**
     * 从 Claims 中提取 employeeId。
     */
    public static String getEmployeeId(Claims claims) {
        return claims.getSubject();
    }

    /**
     * 从 Claims 中提取 tenantCode。
     */
    public static String getTenantCode(Claims claims) {
        return claims.get("tenantCode", String.class);
    }

    /** 姓名声明（C-IDENT：宿主应签发，workflow 的 realName 依赖此声明）。 */
    public static String getName(Claims claims) {
        return claims.get("name", String.class);
    }

    // 契约 v0.2：用户 uid 声明已移除（宿主主键是 VARCHAR 业务 id，与下游本地 BIGINT 用户 id 不同空间）。

    /** 部门编码声明（数据权限部门维度与 workflow 展示依赖此声明）。 */
    public static String getDeptCode(Claims claims) {
        return claims.get("deptCode", String.class);
    }

    /**
     * 从 Claims 中提取 superAdmin。
     */
    public static boolean isSuperAdmin(Claims claims) {
        return Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class));
    }

    /**
     * 从 Claims 中提取 roles。
     */
    @SuppressWarnings("unchecked")
    public static List<String> getRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    /**
     * 从 Claims 中提取 Token 版本号。
     */
    public static int getTokenVersion(Claims claims) {
        Integer ver = claims.get("ver", Integer.class);
        return ver != null ? ver : 0;
    }

    /**
     * 从 Claims 中提取续期次数。
     */
    public static int getRefreshCount(Claims claims) {
        Integer rc = claims.get("rc", Integer.class);
        return rc != null ? rc : 0;
    }

    /**
     * 从 Claims 中提取会话标识。
     */
    public static String getSessionId(Claims claims) {
        String sid = claims.get("sid", String.class);
        return sid != null ? sid : "";
    }

    /**
     * 从 Claims 中判断是否需先修改初始密码。
     */
    public static boolean isPwdResetRequired(Claims claims) {
        return Boolean.TRUE.equals(claims.get("pr", Boolean.class));
    }

    // ==================== 开放平台 Token ====================

    /**
     * 签发开放平台应用 Token。
     *
     * @param appCode          应用编码
     * @param tenantCode       租户编码
     * @param grantedScopes    已授权 scope 列表
     * @param dataScopeType    应用数据范围类型
     * @param dataScopeDeptIds 应用数据范围部门id列表
     * @param employeeId       代理用户工号（user_delegate/authorization_code 模式时有值，client_credentials 为 null）
     * @param secret           签名密钥
     * @return JWT Token
     */
    public static String generateOpenAppToken(String appCode, String tenantCode,
                                              List<String> grantedScopes,
                                              String dataScopeType,
                                              List<String> dataScopeDeptIds,
                                              String employeeId, String secret) {
        SecretKey key = getSecretKey(secret);
        Date now = new Date();
        Date expiration = new Date(now.getTime() + OPEN_APP_EXPIRE_MILLIS);

        var builder = Jwts.builder()
                .subject(appCode)
                .claim("type", "open")
                .claim("tenantCode", tenantCode)
                .claim("grantedScopes", grantedScopes)
                .claim("dataScopeType", dataScopeType)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key);

        if (dataScopeDeptIds != null && !dataScopeDeptIds.isEmpty()) {
            builder.claim("dataScopeDeptIds", dataScopeDeptIds);
        }
        if (employeeId != null && !employeeId.isBlank()) {
            builder.claim("employeeId", employeeId);
        }

        return builder.compact();
    }

    /**
     * 从 Claims 中提取 appCode（开放平台 Token 的 subject）。
     */
    public static String getAppCode(Claims claims) {
        return claims.getSubject();
    }

    /**
     * 判断是否为开放平台 Token。
     */
    public static boolean isOpenToken(Claims claims) {
        return "open".equals(claims.get("type", String.class));
    }

    /**
     * 从 Claims 中提取 grantedScopes。
     */
    @SuppressWarnings("unchecked")
    public static List<String> getGrantedScopes(Claims claims) {
        Object scopes = claims.get("grantedScopes");
        if (scopes instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    /**
     * 从 Claims 中提取 dataScopeType。
     */
    public static String getDataScopeType(Claims claims) {
        return claims.get("dataScopeType", String.class);
    }

    /**
     * 从 Claims 中提取 dataScopeDeptIds。
     */
    @SuppressWarnings("unchecked")
    public static List<String> getDataScopeDeptIds(Claims claims) {
        Object deptIds = claims.get("dataScopeDeptIds");
        if (deptIds instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    /**
     * 从开放平台 Token Claims 中提取代理用户工号。
     */
    public static String getDelegateEmployeeId(Claims claims) {
        return claims.get("employeeId", String.class);
    }

    private static SecretKey getSecretKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT 密钥长度不足 32 字节（当前 " + keyBytes.length + " 字节），请使用足够强度的密钥");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
