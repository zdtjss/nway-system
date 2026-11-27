package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONPath;
import com.alibaba.fastjson2.TypeReference;
import com.nway.system.metadata.service.DictService;
import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiInvokeConfig;
import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.ai.domain.AiOutputField;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.metadata.web.model.DictItemDto;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 HTTP 调用器：组装请求、透传用户 JWT、解析目标响应。
 * <p>
 * 安全边界：目标地址只能来自字典 ai_app_code 的应用 baseUrl（域名+contextPath）、
 * invoke_config.baseUrlOverride，或兜底 sys_app.api_base_url；模型永远不能传入 URL。
 *
 * @author nway
 */
@Slf4j
@Component
public class AiInvoker {

    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_MAX_BYTES = 1024 * 1024;

    /** 应用字典：appCode=应用编码，appName=应用名称，baseUrl=后端接口基础路径（域名+contextPath） */
    private static final String APP_DICT_CODE = "ai_app_code";
    private static final String FIELD_APP_CODE = "appCode";
    private static final String FIELD_BASE_URL = "baseUrl";

    private final AppDao appDao;
    private final DictService dictService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AiInvoker(AppDao appDao, DictService dictService) {
        this.appDao = appDao;
        this.dictService = dictService;
    }

    /**
     * 调用已校验参数的接口。
     */
    public AiInvokeResult invoke(AiInterfaceConfig config, Map<String, Object> args, AiUserContext user) {
        return invoke(config, args, user, null);
    }

    /**
     * 调用已校验参数的接口，并覆盖单次响应字节上限。
     * <p>
     * 覆盖值仅供确定性导出等内部链路按页调整使用，模型无法传入。
     */
    public AiInvokeResult invoke(AiInterfaceConfig config, Map<String, Object> args, AiUserContext user,
                                 Integer maxResponseBytesOverride) {
        if (user == null || StringUtils.isBlank(user.getToken())) {
            return AiInvokeResult.failed(config.getInterfaceCode(), AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文");
        }
        AiInvokeConfig invokeConfig = config.getInvokeConfig();
        String baseUrl = StringUtils.isNotBlank(invokeConfig == null ? null : invokeConfig.getBaseUrlOverride())
                ? invokeConfig.getBaseUrlOverride() : resolveBaseUrl(config.getAppCode());
        if (StringUtils.isBlank(baseUrl)) {
            return AiInvokeResult.failed(config.getInterfaceCode(), AiErrors.TARGET_ERROR,
                    "目标应用未配置基础路径（字典 ai_app_code.baseUrl）：" + config.getAppCode());
        }

        String httpMethod = StringUtils.upperCase(config.getHttpMethod());
        String contentType = StringUtils.defaultIfBlank(config.getContentType(), "application/json");
        Map<String, Object> values = new LinkedHashMap<>(args);

        String path = config.getPath();
        StringBuilder query = new StringBuilder();
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();

        if (invokeConfig != null && invokeConfig.getBodyTemplate() != null && !invokeConfig.getBodyTemplate().isEmpty()) {
            body = JSON.parseObject(JSON.toJSONString(invokeConfig.getBodyTemplate()),
                    new TypeReference<Map<String, Object>>() {
                    });
        }
        if (invokeConfig != null && invokeConfig.getHeaders() != null) {
            headers.putAll(invokeConfig.getHeaders());
        }

        if (config.getInputSchema() == null || config.getInputSchema().isEmpty()) {
            // 未声明入参：支持任意参数，GET/DELETE 透传到 query，其余方法透传到 body
            if ("GET".equals(httpMethod) || "DELETE".equals(httpMethod)) {
                values.forEach((key, value) -> {
                    if (value != null) {
                        query.append(query.length() == 0 ? "" : "&")
                                .append(encode(key)).append('=').append(encode(stringify(value)));
                    }
                });
            } else {
                body.putAll(values);
            }
        } else {
            for (AiFieldDef field : config.getInputSchema()) {
                if (!values.containsKey(field.getKey())) {
                    continue;
                }
                Object value = transformFileValue(field, values.get(field.getKey()), invokeConfig);
                String location = StringUtils.defaultIfBlank(field.getLocation(), "BODY").toUpperCase(Locale.ROOT);
                switch (location) {
                    case "PATH" -> path = path.replace("{" + field.getKey() + "}",
                            encode(String.valueOf(value)));
                    case "QUERY" -> query.append(query.length() == 0 ? "" : "&")
                            .append(encode(field.getKey())).append('=').append(encode(stringify(value)));
                    case "HEADER" -> headers.put(field.getKey(), stringify(value));
                    default -> setBodyValue(body, field, value);
                }
            }
        }

        String url = joinUrl(baseUrl, path);
        if (query.length() > 0) {
            url = url + (url.contains("?") ? "&" : "?") + query;
        }

        String requestBody = null;
        String effectiveContentType = contentType;
        String bodyMode = invokeConfig == null ? "JSON" : StringUtils.defaultIfBlank(invokeConfig.getBodyMode(), "JSON");
        if (!"GET".equals(httpMethod) && !"DELETE".equals(httpMethod)) {
            if ("FORM".equalsIgnoreCase(bodyMode)) {
                effectiveContentType = "application/x-www-form-urlencoded";
                requestBody = toFormBody(body);
            } else if (!body.isEmpty()) {
                requestBody = JSON.toJSONString(body);
            } else {
                requestBody = "{}";
            }
        }

        int timeoutMs = invokeConfig != null && invokeConfig.getTimeoutMs() != null
                ? invokeConfig.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
        int maxBytes = maxResponseBytesOverride != null && maxResponseBytesOverride > 0
                ? maxResponseBytesOverride
                : invokeConfig != null && invokeConfig.getMaxResponseBytes() != null
                ? invokeConfig.getMaxResponseBytes() : DEFAULT_MAX_BYTES;
        int requestBytes = requestBody == null ? 0 : requestBody.getBytes(StandardCharsets.UTF_8).length;
        log.debug("调用业务接口 interfaceCode={} method={} url={} timeoutMs={} requestBytes={}",
                config.getInterfaceCode(), httpMethod, url, timeoutMs, requestBytes);

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)));
            headers.forEach((k, v) -> {
                if (!"authorization".equalsIgnoreCase(k) && StringUtils.isNotBlank(v)) {
                    builder.header(k, v);
                }
            });
            builder.header("Authorization", user.getToken());
            if (StringUtils.isNotBlank(user.effectiveTenantCode())) {
                builder.header("X-Work-Tenant", user.effectiveTenantCode());
            }
            if (StringUtils.isNotBlank(effectiveContentType)) {
                builder.header("Content-Type", effectiveContentType);
            }
            builder.header("Accept", "application/json, application/hal+json, */*");
            if (requestBody == null) {
                builder.method(httpMethod, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(httpMethod, HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
            }

            HttpResponse<byte[]> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            long latency = System.currentTimeMillis() - start;
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            long rawBytes = bytes.length;
            boolean truncated = rawBytes > maxBytes;
            if (truncated) {
                // 只保留上限内的字节用于诊断日志，绝不把截断内容当作成功结果解析：
                // 截断的 JSON 交给模型会诱导其基于残缺数据作答或反复重试。
                byte[] kept = new byte[maxBytes];
                System.arraycopy(bytes, 0, kept, 0, maxBytes);
                bytes = kept;
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("业务接口返回非成功状态 interfaceCode={} method={} url={} status={} latency={}ms body={}",
                        config.getInterfaceCode(), httpMethod, url, response.statusCode(), latency,
                        AiLogs.truncate(text, 300));
                AiInvokeResult result = AiInvokeResult.failed(config.getInterfaceCode(), AiErrors.TARGET_ERROR,
                        "目标系统返回 HTTP " + response.statusCode());
                result.setLatencyMs(latency);
                result.setRawBytes(rawBytes);
                result.setTruncated(truncated);
                return result;
            }
            if (truncated) {
                log.warn("业务接口响应超过读取上限 interfaceCode={} method={} maxBytes={} rawBytes={} latency={}ms",
                        config.getInterfaceCode(), httpMethod, maxBytes, rawBytes, latency);
                AiInvokeResult result = AiInvokeResult.failed(config.getInterfaceCode(),
                        AiErrors.DATA_TOO_LARGE,
                        "目标接口响应超过单次读取上限（" + maxBytes + " 字节），数据不完整，禁止分析或导出");
                result.setLatencyMs(latency);
                result.setRawBytes(rawBytes);
                result.setTruncated(true);
                return result;
            }
            AiInvokeResult result = parse(config, text, latency);
            result.setRawBytes(rawBytes);
            result.setTruncated(false);
            if (result.isSuccess()) {
                log.info("业务接口调用完成 interfaceCode={} method={} status={} latency={}ms requestBytes={} "
                                + "responseBytes={}", config.getInterfaceCode(), httpMethod,
                        response.statusCode(), latency, requestBytes, bytes.length);
            } else {
                log.warn("业务接口返回业务失败 interfaceCode={} code={} message={} latency={}ms",
                        config.getInterfaceCode(), result.getCode(), result.getMessage(), latency);
            }
            return result;
        } catch (HttpTimeoutException e) {
            log.warn("业务接口调用超时 interfaceCode={} method={} url={} timeoutMs={} elapsed={}ms",
                    config.getInterfaceCode(), httpMethod, url, timeoutMs,
                    System.currentTimeMillis() - start);
            AiInvokeResult result = AiInvokeResult.failed(config.getInterfaceCode(), AiErrors.TARGET_TIMEOUT,
                    "目标接口超时（" + timeoutMs + "ms）");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("业务接口调用被中断 interfaceCode={} method={} url={}", config.getInterfaceCode(),
                    httpMethod, url);
            AiInvokeResult result = AiInvokeResult.failed(config.getInterfaceCode(), AiErrors.TARGET_ERROR, "调用被中断");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("调用业务接口异常 interfaceCode={} method={} url={} elapsed={}ms",
                    config.getInterfaceCode(), httpMethod, url, System.currentTimeMillis() - start, e);
            String reason = StringUtils.trimToEmpty(e.getMessage());
            AiInvokeResult result = AiInvokeResult.failed(config.getInterfaceCode(), AiErrors.TARGET_ERROR,
                    StringUtils.isBlank(reason) ? "目标接口调用失败" : "目标接口调用失败：" + reason);
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    /**
     * 供选项数据源使用的简单 HTTP 调用（目标仍限定在字典 ai_app_code 的基础路径下）。
     */
    public Object fetchOptions(String appCode, String method, String path, Map<String, Object> body,
                               String token, String tenantCode, int timeoutMs) {
        String baseUrl = resolveBaseUrl(appCode);
        if (StringUtils.isBlank(baseUrl)) {
            throw new NwaySystemException(AiErrors.OPTION_SOURCE_UNAVAILABLE
                    + "：目标应用未配置基础路径（字典 ai_app_code.baseUrl）");
        }
        String m = StringUtils.defaultIfBlank(method, "POST").toUpperCase(Locale.ROOT);
        String url = joinUrl(baseUrl, path);
        if ("GET".equals(m) && body != null && !body.isEmpty()) {
            StringBuilder query = new StringBuilder();
            body.forEach((k, v) -> {
                if (v != null) {
                    query.append(query.length() == 0 ? "" : "&")
                            .append(encode(k)).append('=').append(encode(stringify(v)));
                }
            });
            if (query.length() > 0) {
                url = url + (url.contains("?") ? "&" : "?") + query;
            }
        }
        long start = System.currentTimeMillis();
        log.debug("查询字段候选值 appCode={} method={} url={} timeoutMs={}", appCode, m, url, timeoutMs);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (StringUtils.isNotBlank(token)) {
                builder.header("Authorization", token);
            }
            if (StringUtils.isNotBlank(tenantCode)) {
                builder.header("X-Work-Tenant", tenantCode);
            }
            if ("GET".equals(m)) {
                builder.GET();
            } else {
                builder.method(m, HttpRequest.BodyPublishers.ofString(
                        JSON.toJSONString(body == null ? Map.of() : body), StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("字段候选值查询失败 appCode={} method={} url={} status={} elapsed={}ms", appCode, m,
                        url, response.statusCode(), System.currentTimeMillis() - start);
                throw new NwaySystemException(AiErrors.OPTION_SOURCE_UNAVAILABLE + "：HTTP " + response.statusCode());
            }
            log.debug("字段候选值查询完成 appCode={} method={} url={} elapsed={}ms", appCode, m, url,
                    System.currentTimeMillis() - start);
            return JSON.parse(response.body());
        } catch (NwaySystemException e) {
            throw e;
        } catch (Exception e) {
            log.warn("字段候选值查询异常 appCode={} method={} url={} elapsed={}ms error={}", appCode, m, url,
                    System.currentTimeMillis() - start, e.getMessage());
            throw new NwaySystemException(AiErrors.OPTION_SOURCE_UNAVAILABLE + "：" + e.getMessage());
        }
    }

    private AiInvokeResult parse(AiInterfaceConfig config, String text, long latency) {
        AiInvokeResult result = new AiInvokeResult();
        result.setInterfaceCode(config.getInterfaceCode());
        result.setLatencyMs(latency);
        Object root;
        try {
            root = StringUtils.isBlank(text) ? null : JSON.parse(text);
        } catch (Exception e) {
            result.setSuccess(true);
            result.setData(text);
            result.setMessage("目标返回非 JSON，已按文本处理");
            return result;
        }
        AiInvokeConfig.Response response = config.getInvokeConfig() == null
                ? null : config.getInvokeConfig().getResponse();
        if (response == null) {
            result.setSuccess(true);
            result.setData(mapOutput(config, null, root, root));
            return result;
        }

        Object code = eval(root, response.getCodePath());
        result.setCode(code);
        if (StringUtils.isNotBlank(response.getCodePath()) && code == null) {
            result.setSuccess(false);
            result.setErrorCode(AiErrors.RESPONSE_PARSE_ERROR);
            result.setMessage("目标响应缺少 codePath：" + response.getCodePath());
            return result;
        }
        if (response.getSuccessCodes() != null && !response.getSuccessCodes().isEmpty() && code != null
                && response.getSuccessCodes().stream().noneMatch(c -> String.valueOf(c).equals(String.valueOf(code)))) {
            result.setSuccess(false);
            result.setErrorCode(AiErrors.TARGET_BUSINESS_ERROR);
            result.setMessage(StringUtils.defaultString(stringifyOrNull(eval(root, response.getMessagePath())),
                    "目标业务返回失败"));
            return result;
        }

        Object data = eval(root, response.getDataPath());
        result.setSuccess(true);
        result.setMessage(stringifyOrNull(eval(root, response.getMessagePath())));

        result.setData(mapOutput(config, response, root, data));
        return result;
    }

    /**
     * 按 outputSchema / outputMapping 映射出参；都没配时原样返回响应体。
     * <p>
     * outputSchema 是唯一出参口径：声明了 properties / items.properties 的对象或数组，
     * 只保留声明的子字段，未声明的字段不会返回给模型和前端。
     */
    private Object mapOutput(AiInterfaceConfig config, AiInvokeConfig.Response response, Object root, Object data) {
        if (config.getOutputSchema() != null && !config.getOutputSchema().isEmpty()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (AiOutputField field : config.getOutputSchema()) {
                String path = StringUtils.defaultIfBlank(field.getPath(), "$." + field.getKey());
                Object value = eval(root, path);
                mapped.put(field.getKey(), projectOutput(value, field));
            }
            return mapped;
        }
        if (response != null && response.getOutputMapping() != null && !response.getOutputMapping().isEmpty()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            response.getOutputMapping().forEach((k, path) -> mapped.put(k, eval(root, path)));
            return mapped;
        }
        return data == null ? root : data;
    }

    /**
     * 按出参字段定义裁剪值：声明了子结构时只保留声明的字段，sensitive 字段脱敏。
     */
    private Object projectOutput(Object value, AiOutputField field) {
        if (value == null) {
            return null;
        }
        Object projected = projectChildren(value, field);
        return Boolean.TRUE.equals(field.getSensitive()) ? mask(projected) : projected;
    }

    private Object projectChildren(Object value, AiOutputField field) {
        if (value instanceof Map<?, ?> map) {
            List<AiOutputField> properties = field.getProperties();
            if (properties == null || properties.isEmpty()) {
                return value;
            }
            Map<String, Object> projected = new LinkedHashMap<>();
            for (AiOutputField property : properties) {
                if (map.containsKey(property.getKey())) {
                    projected.put(property.getKey(), projectOutput(map.get(property.getKey()), property));
                }
            }
            return projected;
        }
        if (value instanceof Collection<?> collection) {
            List<AiOutputField> properties = field.getItems() == null ? null : field.getItems().getProperties();
            if (properties == null || properties.isEmpty()) {
                return value;
            }
            List<Object> projected = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> child = new LinkedHashMap<>();
                    for (AiOutputField property : properties) {
                        if (map.containsKey(property.getKey())) {
                            child.put(property.getKey(), projectOutput(map.get(property.getKey()), property));
                        }
                    }
                    projected.add(child);
                } else {
                    projected.add(item);
                }
            }
            return projected;
        }
        return value;
    }

    private Object eval(Object root, String path) {
        if (root == null || StringUtils.isBlank(path)) {
            return null;
        }
        try {
            return JSONPath.eval(root, path);
        } catch (Exception e) {
            return null;
        }
    }

    private Object mask(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        if (s.length() <= 4) {
            return "****";
        }
        return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
    }

    /**
     * 目标应用基础路径：优先字典 ai_app_code 的 baseUrl（域名+contextPath），兼容存量 sys_app.api_base_url。
     * <p>
     * 不缓存字典结果，保证字典调整后接口调试与大模型调用立即生效。
     */
    private String resolveBaseUrl(String appCode) {
        if (StringUtils.isBlank(appCode)) {
            return null;
        }
        String dictBaseUrl = resolveDictBaseUrl(appCode);
        if (StringUtils.isNotBlank(dictBaseUrl)) {
            return dictBaseUrl;
        }
        App app = TenantUtil.callAsSystem(() -> appDao.getByCode(appCode));
        if (app == null || StringUtils.isBlank(app.getApiBaseUrl())) {
            return "";
        }
        return app.getApiBaseUrl().trim();
    }

    private String resolveDictBaseUrl(String appCode) {
        try {
            List<DictItemDto> items = TenantUtil.callAsSystem(
                    () -> dictService.queryByCode(APP_DICT_CODE));
            for (DictItemDto item : items) {
                Map<String, Object> value = item.getItemValue();
                if (value == null || value.get(FIELD_APP_CODE) == null) {
                    continue;
                }
                if (!appCode.equalsIgnoreCase(String.valueOf(value.get(FIELD_APP_CODE)).trim())) {
                    continue;
                }
                Object baseUrl = value.get(FIELD_BASE_URL);
                return baseUrl == null ? null : StringUtils.trimToNull(String.valueOf(baseUrl));
            }
        } catch (Exception e) {
            log.warn("读取应用字典失败 dict={} appCode={} error={}", APP_DICT_CODE, appCode, e.getMessage());
        }
        return null;
    }

    private void setBodyValue(Map<String, Object> body, AiFieldDef field, Object value) {
        String path = StringUtils.defaultIfBlank(field.getJsonPath(), field.getKey());
        String[] parts = path.split("\\.");
        Map<String, Object> current = body;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(parts[i], nested);
                current = nested;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) next;
                current = nested;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    private Object transformFileValue(AiFieldDef field, Object value, AiInvokeConfig invokeConfig) {
        if (value == null || invokeConfig == null || invokeConfig.getFileMapping() == null) {
            return value;
        }
        String mode = invokeConfig.getFileMapping().get(field.getKey());
        if (StringUtils.isBlank(mode)) {
            return value;
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "json-string" -> JSON.toJSONString(value);
            case "json-array-string" -> JSON.toJSONString(
                    value instanceof List ? value : List.of(value));
            case "string" -> String.valueOf(value);
            default -> value;
        };
    }

    private String toFormBody(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder();
        body.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encode(k)).append('=').append(encode(stringify(v)));
        });
        return sb.toString();
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = StringUtils.defaultString(path);
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        // 容错：接口 path 误配成包含应用 contextPath（如 /nway-system/user/queryPage）时去除重复前缀，
        // 避免拼出 /nway-system/nway-system/user/queryPage 导致 404。
        String basePath = basePath(base);
        if (StringUtils.isNotBlank(basePath) && (p.equals(basePath) || p.startsWith(basePath + "/"))) {
            log.warn("接口 path 已包含应用基础路径，已自动去除重复前缀 baseUrl={} path={}", baseUrl, path);
            p = p.substring(basePath.length());
            if (p.isEmpty()) {
                p = "/";
            }
        }
        return base + p;
    }

    /** 基础路径中的 contextPath；无路径或仅 / 时返回 null */
    private String basePath(String baseUrl) {
        try {
            String path = URI.create(baseUrl).getPath();
            if (StringUtils.isBlank(path) || "/".equals(path)) {
                return null;
            }
            return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        } catch (Exception e) {
            return null;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof java.util.Collection<?> || value instanceof Map) {
            return JSON.toJSONString(value);
        }
        return String.valueOf(value);
    }

    private String stringifyOrNull(Object value) {
        return value == null ? null : stringify(value);
    }
}
