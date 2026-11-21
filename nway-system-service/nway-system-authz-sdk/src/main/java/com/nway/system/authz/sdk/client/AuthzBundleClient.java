package com.nway.system.authz.sdk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.authz.sdk.config.AuthzProperties;
import com.nway.system.authz.sdk.model.AuthzBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * 策略包客户端：拉取平台版本化策略包并在本地判定。
 * <p>
 * 可靠性约定：
 * <ul>
 *   <li>按版本缓存：平台返回新版本即替换（无需逐用户失效）；</li>
 *   <li>stale-while-error：平台不可用时沿用最后一次成功结果（上限 {@code stale-ttl}）；</li>
 *   <li>无历史结果且 {@code fail-open=false}（默认）时拒绝。</li>
 * </ul>
 */
public class AuthzBundleClient {

    private static final Logger log = LoggerFactory.getLogger(AuthzBundleClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TOKEN_REFRESH_AHEAD_MS = 5 * 60 * 1000L;

    /** 传输层抽象（便于测试替换） */
    public interface Transport {
        Response get(String url, String bearerToken, String etag) throws Exception;
    }

    public record Response(int status, String body, String etag) {
    }

    private final AuthzProperties properties;
    private final Transport transport;
    private final HttpClient httpClient;

    private final Cache<String, AuthzBundle> cache;
    private final Cache<String, AuthzBundle> staleCache;
    private final Cache<String, String> etagCache;

    private volatile String cachedToken;
    private volatile long tokenExpireAtMillis;

    /** 运行指标（供健康检查/管理端展示，不依赖 Micrometer） */
    private final LongAdder decisions = new LongAdder();
    private final LongAdder denies = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private volatile long lastErrorAt;

    public record Stats(long decisions, long denies, long errors, long lastErrorAt, long cachedBundles) {
    }

    public Stats stats() {
        return new Stats(decisions.sum(), denies.sum(), errors.sum(), lastErrorAt, cache.estimatedSize());
    }

    public AuthzBundleClient(AuthzProperties properties) {
        this(properties, null);
    }

    public AuthzBundleClient(AuthzProperties properties, Transport transport) {
        this.properties = properties;
        this.transport = transport;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(properties.getCacheTtl())
                .build();
        this.staleCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(properties.getStaleTtl())
                .build();
        this.etagCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(properties.getStaleTtl())
                .build();
    }

    /** 获取策略包（有效缓存 → 平台 → stale 缓存）。 */
    public AuthzBundle bundle(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return emptyBundle();
        }
        AuthzBundle cached = cache.getIfPresent(employeeId);
        if (cached != null) {
            return cached;
        }
        try {
            AuthzBundle fetched = fetch(employeeId);
            cache.put(employeeId, fetched);
            staleCache.put(employeeId, fetched);
            return fetched;
        } catch (RuntimeException e) {
            errors.increment();
            lastErrorAt = System.currentTimeMillis();
            AuthzBundle stale = staleCache.getIfPresent(employeeId);
            if (stale != null) {
                log.warn("平台策略不可用，沿用历史策略包（stale-while-error）employee={}, reason={}",
                        employeeId, e.getMessage());
                return stale;
            }
            if (properties.isFailOpen()) {
                log.error("策略不可用且无历史缓存，按 fail-open 放行（应急开关）employee={}", employeeId);
                return emptyBundle();
            }
            log.warn("策略不可用且无历史缓存，按 fail-closed 拒绝 employee={}, reason={}",
                    employeeId, e.getMessage());
            return null;
        }
    }

    public boolean allows(String employeeId, String resourceCode, String action) {
        AuthzBundle bundle = bundle(employeeId);
        decisions.increment();
        boolean allowed = bundle != null && AuthzEvaluator.allows(bundle, resourceCode, action);
        if (!allowed) {
            denies.increment();
        }
        return allowed;
    }

    public List<AuthzBundle.DataPredicate> predicates(String employeeId, String resourceCode) {
        AuthzBundle bundle = bundle(employeeId);
        return bundle == null ? List.of() : AuthzEvaluator.predicates(bundle, resourceCode);
    }

    /**
     * 拉取策略包，带 If-None-Match 协商：
     * 平台返回 304 时复用本地（stale 缓存中的）同版本策略包，不重复解析。
     */
    private AuthzBundle fetch(String employeeId) {
        String url = baseUrl() + "/open/permission/bundle?employeeId="
                + URLEncoder.encode(employeeId, StandardCharsets.UTF_8);
        String etag = etagCache.getIfPresent(employeeId);
        Response response = doGet(url, etag);
        if (response.status() == 401 && hasAppSecret()) {
            refreshToken();
            response = doGet(url, etag);
        }
        if (response.status() == 304) {
            AuthzBundle unchanged = staleCache.getIfPresent(employeeId);
            if (unchanged != null) {
                cache.put(employeeId, unchanged);
                log.debug("策略包未变更（304），沿用本地版本 employee={}, version={}",
                        employeeId, unchanged.version());
                return unchanged;
            }
            // 本地已无对应版本（stale 过期），去掉协商头重新拉全量
            response = doGet(url, null);
            if (response.status() == 401 && hasAppSecret()) {
                refreshToken();
                response = doGet(url, null);
            }
        }
        if (response.status() != 200) {
            throw new IllegalStateException("拉取策略包失败 status=" + response.status());
        }
        AuthzBundle bundle;
        try {
            JsonNode root = MAPPER.readTree(response.body());
            if (root.path("code").asInt(500) != 200) {
                throw new IllegalStateException("平台返回错误: " + root.path("msg").asText(""));
            }
            bundle = MAPPER.convertValue(root.path("data"), AuthzBundle.class);
        } catch (Exception e) {
            throw new IllegalStateException("解析策略包失败", e);
        }
        String responseEtag = response.etag() != null && !response.etag().isBlank()
                ? response.etag() : String.valueOf(bundle.version());
        etagCache.put(employeeId, responseEtag);
        return bundle;
    }

    private Response doGet(String url, String etag) {
        try {
            return transport != null
                    ? transport.get(url, currentToken(), etag)
                    : httpGet(url, currentToken(), etag);
        } catch (Exception e) {
            throw new IllegalStateException("拉取策略包失败", e);
        }
    }

    private boolean hasAppSecret() {
        return properties.getAppSecret() != null && !properties.getAppSecret().isBlank();
    }

    private Response httpGet(String url, String token, String etag) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .timeout(properties.getRequestTimeout())
                .GET();
        if (etag != null && !etag.isBlank()) {
            builder.header("If-None-Match", etag);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(),
                response.headers().firstValue("ETag").orElse(null));
    }

    private String currentToken() {
        if (properties.getAppSecret() == null || properties.getAppSecret().isBlank()) {
            if (properties.getAppToken() == null || properties.getAppToken().isBlank()) {
                throw new IllegalStateException("nway.authz.app-secret 与 app-token 至少配置一项");
            }
            return properties.getAppToken();
        }
        if (cachedToken == null || System.currentTimeMillis() > tokenExpireAtMillis - TOKEN_REFRESH_AHEAD_MS) {
            refreshToken();
        }
        return cachedToken;
    }

    private synchronized void refreshToken() {
        try {
            String form = "appCode=" + URLEncoder.encode(properties.getAppCode(), StandardCharsets.UTF_8)
                    + "&appSecret=" + URLEncoder.encode(properties.getAppSecret(), StandardCharsets.UTF_8)
                    + "&grantType=client_credentials";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/open/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(properties.getRequestTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(response.body());
            if (root.path("code").asInt(500) != 200) {
                throw new IllegalStateException("换取应用 Token 失败: " + root.path("msg").asText(""));
            }
            JsonNode data = root.path("data");
            String token = data.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Token 响应缺少 access_token");
            }
            cachedToken = token;
            tokenExpireAtMillis = System.currentTimeMillis() + data.path("expires_in").asLong(7200) * 1000;
            log.info("已获取开放平台 Token，appCode={}", properties.getAppCode());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("换取应用 Token 异常", e);
        }
    }

    private String baseUrl() {
        String url = properties.getPlatformUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static AuthzBundle emptyBundle() {
        return new AuthzBundle(0L, null, List.of());
    }

    /** 供健康检查/测试使用。 */
    public void clear() {
        cache.invalidateAll();
        staleCache.invalidateAll();
        etagCache.invalidateAll();
    }

    /**
     * 启动自检：验证 Token 获取与策略包接口连通性。
     * <p>
     * 使用不存在的工号探测，平台会返回空策略包；任何网络/鉴权问题都会抛异常。
     */
    public void probe() {
        fetch("__nway_authz_startup_probe__");
    }

    /** 便于测试注入固定缓存时长。 */
    Duration cacheTtl() {
        return properties.getCacheTtl();
    }
}
