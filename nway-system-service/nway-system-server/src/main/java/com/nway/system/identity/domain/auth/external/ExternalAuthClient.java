package com.nway.system.identity.domain.auth.external;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nway.system.common.exception.NwaySystemException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 外部身份认证客户端。
 * <p>
 * 只做 HTTP 调用与响应映射，不包含任何具体身份源（AD/OAM/...）逻辑：
 * 身份校验、组织与岗位聚合均由外部系统完成。
 *
 * @author nway
 */
@Slf4j
@Component
public class ExternalAuthClient {

    /** 外部系统判定「账号不存在」，可触发平台本地回退 */
    public static final int ACCOUNT_NOT_FOUND = 1017;
    /** 外部认证服务不可用 */
    public static final int SERVICE_UNAVAILABLE = 1015;
    /** 外部认证未配置或未启用 */
    public static final int NOT_CONFIGURED = 1016;
    /** 外部系统未返回可用的用户标识 */
    public static final int USER_NOT_FOUND = 1020;

    /** 内部调用凭据请求头，与外部认证服务的校验规则保持一致 */
    private static final String TOKEN_HEADER = "X-Nway-Token";

    private final ExternalAuthProperties properties;

    private volatile HttpClient httpClient;

    @Autowired
    public ExternalAuthClient(ExternalAuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 调用外部系统校验工号密码。
     *
     * @return 外部系统的用户档案
     */
    public ExternalUser authenticate(String account, String password) {
        assertConfigured();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", account);
        payload.put("password", password);
        JSONObject body = post(properties.getLoginPath(), payload);
        assertSuccess(body);
        JSONObject data = body.getJSONObject("data");
        if (data == null) {
            throw new NwaySystemException(USER_NOT_FOUND, "外部认证未返回用户信息");
        }
        return toUser(data);
    }

    /**
     * 外部系统自助改密（需旧密码校验）。
     *
     * @param account     工号
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    public void changePassword(String account, String oldPassword, String newPassword) {
        assertConfigured();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", account);
        payload.put("oldPassword", oldPassword);
        payload.put("newPassword", newPassword);
        payload.put("mode", "expired");
        JSONObject body = post(properties.getChangePasswordPath(), payload);
        assertSuccess(body);
    }

    private JSONObject post(String path, Map<String, Object> payload) {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + path))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                    .header("Content-Type", "application/json")
                    .header(TOKEN_HEADER, StringUtils.defaultString(properties.getToken()))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload), StandardCharsets.UTF_8))
                    .build();
            response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NwaySystemException(SERVICE_UNAVAILABLE, "外部认证服务调用被中断");
        } catch (Exception e) {
            log.warn("调用外部认证服务失败", e);
            throw new NwaySystemException(SERVICE_UNAVAILABLE, "外部认证服务不可用，请稍后重试");
        }
        if (response.statusCode() != 200) {
            log.warn("外部认证服务返回异常 status={}", response.statusCode());
            throw new NwaySystemException(SERVICE_UNAVAILABLE,
                    "外部认证服务返回异常（HTTP " + response.statusCode() + "）");
        }
        JSONObject body = JSON.parseObject(response.body());
        if (body == null) {
            throw new NwaySystemException(SERVICE_UNAVAILABLE, "外部认证服务返回内容为空");
        }
        return body;
    }

    /**
     * 外部服务统一 Result 结构：code=200 视为成功，其余透传 code/msg。
     */
    static void assertSuccess(JSONObject body) {
        int code = body.getIntValue("code", 500);
        if (code != 200) {
            String message = body.getString("msg");
            throw new NwaySystemException(code, StringUtils.defaultIfBlank(message, "登录失败"));
        }
    }

    static ExternalUser toUser(JSONObject data) {
        ExternalUser user = new ExternalUser();

        String employeeId = data.getString("empid");
        if (employeeId == null) {
            employeeId = data.getString("user_id");
        }
        Optional.ofNullable(employeeId).ifPresent(user::setEmployeeId);

        String realName = data.getString("cnname");
        if (realName == null) {
            realName = data.getString("displayname");
        }
        Optional.ofNullable(realName).ifPresent(user::setRealName);

        String email = data.getString("email");
        Optional.ofNullable(email).ifPresent(user::setEmail);

        String avatar = data.getString("avatar");
        if (avatar == null) {
            avatar = data.getString("userimgurl");
        }
        Optional.ofNullable(avatar).ifPresent(user::setAvatar);

        String mobile = data.getString("mobile");
        if (mobile == null) {
            mobile = data.getString("mobilephone");
        }
        Optional.ofNullable(mobile).ifPresent(user::setMobile);
        return user;
    }

    private void assertConfigured() {
        if (!properties.isEnabled()) {
            throw new NwaySystemException(NOT_CONFIGURED, "外部身份认证登录未启用");
        }
        if (!properties.isConfigured()) {
            throw new NwaySystemException(NOT_CONFIGURED, "外部认证服务地址或内部凭据未配置");
        }
    }

    private HttpClient client() {
        HttpClient current = httpClient;
        if (current == null) {
            synchronized (this) {
                current = httpClient;
                if (current == null) {
                    current = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                            .build();
                    httpClient = current;
                }
            }
        }
        return current;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 外部系统返回的登录用户最小档案。
     */
    @Data
    public static class ExternalUser {
        private String employeeId;
        private String realName;
        private String email;
        private String mobile;
        private String avatar;
    }
}
