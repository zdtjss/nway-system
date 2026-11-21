package com.nway.system.authz.sdk.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.authz.sdk.model.ResourcePolicy;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 统一权限策略 REST 客户端（带本地缓存 + 熔断 + Token 自动刷新）。
 * <p>
 * 调用平台 {@code GET /open/permission/policies?employeeId=xxx} 获取用户在本应用的权限策略。
 * 本地 Caffeine 缓存（1 分钟 TTL），平台不可用时 fail-closed（返回空列表 = 拒绝所有）。
 * <p>
 * Token 管理：配置了 appSecret 时，自动通过 {@code POST /open/token}
 * （grantType=client_credentials）获取应用 Token，并在过期前 5 分钟自动刷新；
 * 收到 401 时强制刷新并重试一次。仅配置 appToken 时使用静态 Token（不推荐）。
 *
 * @author nway
 * @deprecated 平台正在下线 {@code /open/permission/policies}（响应已带 {@code Deprecation: true}）。
 *         请改用 {@link AuthzBundleClient} + {@link com.nway.system.authz.sdk.filter.PredicateSqlBuilder}：
 *         bundle 是平台<b>已判定</b>的结果（DENY 已扣除、租户已收敛、带版本/ETag），
 *         且支持 {@code DEPT_TREE}/{@code DEPT_LIST} 谓词；旧的 {@code ResourcePolicy.dataScope}
 *         值域（DEPT_WITH_SUB/CUSTOM）与 bundle 不一致，不建议继续新增使用。
 */
@Deprecated
@Slf4j
public class PermissionPolicyClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Token 剩余不足 5 分钟时提前刷新 */
    private static final long REFRESH_AHEAD_MILLIS = 5 * 60 * 1000L;

    private final HttpClient httpClient;
    private final String platformBaseUrl;
    private final String appCode;
    private final String appToken;
    private final String appSecret;

    /** 自动刷新的 Token 缓存（appSecret 模式） */
    private volatile String cachedToken;
    private volatile long tokenExpireAtMillis;

    /** 策略缓存：employeeId → 策略列表 */
    private final Cache<String, List<ResourcePolicy>> policyCache;

    /** 熔断标记 */
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenSince = 0;
    private static final long CIRCUIT_RECOVERY_MILLIS = 30_000;

    /**
     * 创建客户端（推荐：appSecret 模式，Token 自动获取与刷新）。
     *
     * @param platformBaseUrl 平台地址（如 http://localhost:8081/nway-system）
     * @param appCode         本应用标识（如 order-system）
     * @param appSecret       应用密钥
     */
    public PermissionPolicyClient(String platformBaseUrl, String appCode, String appSecret) {
        this(platformBaseUrl, appCode, null, appSecret);
    }

    /**
     * 创建客户端（完整参数）。
     *
     * @param platformBaseUrl 平台地址
     * @param appCode         本应用标识
     * @param appToken        静态应用 Token（可空，已过时）
     * @param appSecret       应用密钥（非空时自动管理 Token）
     */
    public PermissionPolicyClient(String platformBaseUrl, String appCode, String appToken, String appSecret) {
        this.platformBaseUrl = platformBaseUrl.endsWith("/")
                ? platformBaseUrl.substring(0, platformBaseUrl.length() - 1)
                : platformBaseUrl;
        this.appCode = appCode;
        this.appToken = appToken;
        this.appSecret = appSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.policyCache = Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
        if ((appToken == null || appToken.isBlank()) && (appSecret == null || appSecret.isBlank())) {
            throw new IllegalArgumentException("appSecret 与 appToken 至少配置一项");
        }
    }

    @PreDestroy
    public void shutdown() {
        policyCache.invalidateAll();
    }

    /**
     * 获取用户在本应用的全部权限策略（带缓存）。
     */
    @Deprecated
    public List<ResourcePolicy> getPolicies(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return policyCache.get(employeeId, this::fetchFromPlatform);
        } catch (RuntimeException e) {
            log.warn("获取策略失败 employeeId={}, error={}", employeeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户在指定资源上的策略。
     * <p>
     * 精确资源优先，{@code *} 仅在无精确策略时兜底。不能依赖列表顺序（findFirst 同时匹配两者时，
     * 命中哪条取决于平台返回顺序，可能把通配策略当成精确策略而放大权限）。
     */
    @Deprecated
    public ResourcePolicy getPolicy(String employeeId, String resourceCode) {
        List<ResourcePolicy> all = getPolicies(employeeId);
        ResourcePolicy exact = all.stream()
                .filter(p -> resourceCode.equals(p.resourceCode()))
                .findFirst()
                .orElse(null);
        if (exact != null) {
            return exact;
        }
        return all.stream()
                .filter(p -> "*".equals(p.resourceCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断用户是否有指定资源的指定操作权限。
     */
    @Deprecated
    public boolean hasAction(String employeeId, String resourceCode, String action) {
        ResourcePolicy policy = getPolicy(employeeId, resourceCode);
        return policy != null && policy.hasAction(action);
    }

    /**
     * 失效指定用户的缓存。
     */
    public void evict(String employeeId) {
        policyCache.invalidate(employeeId);
    }

    /**
     * 失效全部缓存。
     */
    public void evictAll() {
        policyCache.invalidateAll();
    }

    // ---- 内部方法 ----

    private List<ResourcePolicy> fetchFromPlatform(String employeeId) {
        if (isCircuitOpen()) {
            throw new IllegalStateException("平台熔断中");
        }
        try {
            List<ResourcePolicy> result = doFetch(employeeId, currentToken());
            resetCircuit();
            return result;
        } catch (UnauthorizedException e) {
            // 401：Token 失效，强制刷新后重试一次
            if (appSecret != null && !appSecret.isBlank()) {
                refreshToken();
                List<ResourcePolicy> result = doFetch(employeeId, currentToken());
                resetCircuit();
                return result;
            }
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            openCircuit();
            throw new RuntimeException("获取策略异常", e);
        }
    }

    private List<ResourcePolicy> doFetch(String employeeId, String token) {
        try {
            String encodedId = URLEncoder.encode(employeeId, StandardCharsets.UTF_8);
            String url = platformBaseUrl + "/open/permission/policies?employeeId=" + encodedId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("X-App-Code", appCode)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new UnauthorizedException("Token 无效或已过期");
            }
            if (response.statusCode() == 200) {
                JsonNode root = MAPPER.readTree(response.body());
                int code = root.path("code").asInt(500);
                if (code == 200) {
                    JsonNode data = root.path("data");
                    if (data.isArray()) {
                        return MAPPER.convertValue(data, new TypeReference<List<ResourcePolicy>>() {});
                    }
                }
            }
            log.warn("获取策略失败 employeeId={}, status={}", employeeId, response.statusCode());
            throw new IllegalStateException("获取策略失败 status=" + response.statusCode());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("获取策略异常", e);
        }
    }

    // ==================== Token 管理 ====================

    /**
     * 获取当前可用 Token：appSecret 模式下自动获取/刷新，否则返回静态 appToken。
     */
    private String currentToken() {
        if (appSecret == null || appSecret.isBlank()) {
            return appToken;
        }
        if (cachedToken == null || System.currentTimeMillis() > tokenExpireAtMillis - REFRESH_AHEAD_MILLIS) {
            refreshToken();
        }
        return cachedToken;
    }

    /**
     * 调用 POST /open/token 获取新 Token（grantType=client_credentials）。
     */
    private synchronized void refreshToken() {
        try {
            String form = "appCode=" + URLEncoder.encode(appCode, StandardCharsets.UTF_8)
                    + "&appSecret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8)
                    + "&grantType=client_credentials";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformBaseUrl + "/open/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(response.body());
            int code = root.path("code").asInt(500);
            if (code != 200) {
                throw new IllegalStateException("获取开放平台 Token 失败: " + root.path("msg").asText(""));
            }
            JsonNode data = root.path("data");
            String token = data.path("access_token").asText(null);
            long expiresIn = data.path("expires_in").asLong(7200);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("开放平台 Token 响应缺少 access_token");
            }
            this.cachedToken = token;
            this.tokenExpireAtMillis = System.currentTimeMillis() + expiresIn * 1000;
            log.info("已获取开放平台应用 Token，appCode={}，有效期 {}s", appCode, expiresIn);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("获取开放平台 Token 异常", e);
        }
    }

    /** 平台返回 401 的标记异常 */
    private static class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String message) {
            super(message);
        }
    }

    private boolean isCircuitOpen() {
        if (!circuitOpen) return false;
        if (System.currentTimeMillis() - circuitOpenSince > CIRCUIT_RECOVERY_MILLIS) {
            circuitOpen = false;
            return false;
        }
        return true;
    }

    private void openCircuit() {
        circuitOpen = true;
        circuitOpenSince = System.currentTimeMillis();
    }

    private void resetCircuit() {
        circuitOpen = false;
    }
}
