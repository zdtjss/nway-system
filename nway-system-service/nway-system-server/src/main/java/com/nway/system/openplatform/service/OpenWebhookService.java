package com.nway.system.openplatform.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.openplatform.dao.SysOpenWebhookDao;
import com.nway.system.openplatform.entity.SysOpenWebhook;
import com.nway.system.openplatform.web.model.WebhookVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开放平台 Webhook 事件回调服务。
 * <p>
 * 使用虚拟线程异步发送 HTTP POST 回调，附 HMAC-SHA256 签名。
 * 失败按 retry_count 次重试，指数退避。
 *
 * @author nway
 */
@Slf4j
@Service
public class OpenWebhookService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    private SysOpenWebhookDao sysOpenWebhookDao;

    @Autowired
    private TenantNameResolver tenantNameResolver;

    /**
     * 发布事件：查询订阅该事件的所有 Webhook，异步发送回调。
     *
     * @param eventType 事件类型（如 user.created, dept.changed）
     * @param data      事件数据（将被序列化为 JSON）
     */
    @Async
    public void publishEvent(String eventType, Object data) {
        List<SysOpenWebhook> webhooks = sysOpenWebhookDao.queryByEvent(eventType);
        if (CollectionUtils.isEmpty(webhooks)) {
            return;
        }
        String payload = JSON.toJSONString(Map.of(
                "eventType", eventType,
                "timestamp", System.currentTimeMillis(),
                "data", data
        ));
        for (SysOpenWebhook webhook : webhooks) {
            sendWebhook(webhook, payload);
        }
    }

    /**
     * 发送单个 Webhook 回调（含重试）。
     */
    private void sendWebhook(SysOpenWebhook webhook, String payload) {
        int maxRetry = webhook.getRetryCount() != null ? webhook.getRetryCount() : 3;
        String signature = hmacSha256(webhook.getSecret(), payload);

        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhook.getCallbackUrl()))
                        .header("Content-Type", "application/json")
                        .header("X-Webhook-Signature", signature)
                        .header("X-Webhook-Event", webhook.getEventType())
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("Webhook 回调成功: url={}, event={}", webhook.getCallbackUrl(), webhook.getEventType());
                    return;
                }
                log.warn("Webhook 回调失败: url={}, status={}, attempt={}/{}",
                        webhook.getCallbackUrl(), response.statusCode(), attempt, maxRetry);
            } catch (Exception e) {
                log.warn("Webhook 回调异常: url={}, attempt={}/{}, error={}",
                        webhook.getCallbackUrl(), attempt, maxRetry, e.getMessage());
            }
            // 指数退避: 1s, 2s, 4s, 8s...
            if (attempt < maxRetry) {
                try {
                    Thread.sleep(Duration.ofSeconds((long) Math.pow(2, attempt)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Webhook 回调最终失败: url={}, event={}", webhook.getCallbackUrl(), webhook.getEventType());
    }

    /**
     * 测试 Webhook 回调（同步，管理后台使用）。
     *
     * @return HTTP 状态码
     */
    public int testWebhook(String webhookId) {
        SysOpenWebhook webhook = sysOpenWebhookDao.getById(webhookId);
        if (webhook == null) {
            return 404;
        }
        String payload = JSON.toJSONString(Map.of(
                "eventType", "test",
                "timestamp", System.currentTimeMillis(),
                "data", Map.of("message", "webhook test")
        ));
        String signature = hmacSha256(webhook.getSecret(), payload);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getCallbackUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", signature)
                    .header("X-Webhook-Event", "test")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (Exception e) {
            log.error("Webhook 测试回调失败: url={}", webhook.getCallbackUrl(), e);
            return 500;
        }
    }

    // ==================== Webhook CRUD ====================

    public void createWebhook(SysOpenWebhook webhook) {
        if (!StringUtils.hasText(webhook.getEventType()) || !StringUtils.hasText(webhook.getCallbackUrl())) {
            throw new IllegalArgumentException("事件类型和回调地址不能为空");
        }
        if (!StringUtils.hasText(webhook.getStatus())) {
            webhook.setStatus("using");
        }
        if (webhook.getRetryCount() == null) {
            webhook.setRetryCount(3);
        }
        sysOpenWebhookDao.insert(webhook);
    }

    public void updateWebhook(SysOpenWebhook webhook) {
        SysOpenWebhook existing = sysOpenWebhookDao.getById(webhook.getId());
        if (existing == null) {
            throw new NwaySystemException("Webhook 不存在");
        }
        // 归属租户不可通过更新接口变更：以库中记录为准并校验写权限
        webhook.setTenantCode(existing.getTenantCode());
        TenantUtil.assertAdminCanManageTenant(existing.getTenantCode());
        sysOpenWebhookDao.updateById(webhook);
    }

    public void deleteWebhook(String id) {
        sysOpenWebhookDao.deleteById(id);
    }

    public SysOpenWebhook getById(String id) {
        return sysOpenWebhookDao.getById(id);
    }

    public List<WebhookVo> queryByApp(String appId) {
        List<SysOpenWebhook> webhooks = sysOpenWebhookDao.queryByApp(appId);
        if (CollectionUtils.isEmpty(webhooks)) {
            return List.of();
        }
        List<WebhookVo> voList = ConverterUtil.convert(webhooks, WebhookVo.class);
        Map<String, String> tenantNameMap = tenantNameResolver.resolveNames(
                voList.stream().map(WebhookVo::getTenantCode).collect(Collectors.toSet()));
        voList.forEach(vo -> {
            if (StringUtils.hasText(vo.getTenantCode())) {
                vo.setTenantName(tenantNameMap.getOrDefault(vo.getTenantCode(), ""));
            }
        });
        return voList;
    }

    // ==================== Private ====================

    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }
}
