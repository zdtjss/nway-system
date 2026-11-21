package com.nway.system.openplatform.web.admin;

import com.nway.system.openplatform.web.model.WebhookVo;
import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.openplatform.service.OpenWebhookService;
import com.nway.system.common.web.Result;
import com.nway.system.openplatform.entity.SysOpenWebhook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 开放平台 Webhook 管理 Controller（内部管理后台）。
 *
 * @author nway
 */
@RequiresPermission(resource = "open_webhook")
@RestController
@RequestMapping("openWebhook")
public class OpenWebhookController {

    @Autowired
    private OpenWebhookService openWebhookService;

    /**
     * 按应用查询 Webhook 列表。
     */
    @GetMapping("queryByApp")
    public Result<List<com.nway.system.openplatform.web.model.WebhookVo>> queryByApp(String appId) {
        return Result.ok(openWebhookService.queryByApp(appId));
    }

    /**
     * 按 ID 查询 Webhook。
     */
    @GetMapping("getById")
    public Result<SysOpenWebhook> getById(String id) {
        return Result.ok(openWebhookService.getById(id));
    }

    /**
     * 创建 Webhook。
     */
    @PostMapping("create")
    public Result<Void> create(@RequestBody SysOpenWebhook webhook) {
        openWebhookService.createWebhook(webhook);
        return Result.ok();
    }

    /**
     * 更新 Webhook。
     */
    @PostMapping("update")
    public Result<Void> update(@RequestBody SysOpenWebhook webhook) {
        openWebhookService.updateWebhook(webhook);
        return Result.ok();
    }

    /**
     * 删除 Webhook。
     */
    @PostMapping("delete")
    public Result<Void> delete(String id) {
        openWebhookService.deleteWebhook(id);
        return Result.ok();
    }

    /**
     * 测试 Webhook 回调。
     */
    @PostMapping("test")
    public Result<Map<String, Object>> test(String webhookId) {
        int statusCode = openWebhookService.testWebhook(webhookId);
        return Result.ok(Map.of("statusCode", statusCode));
    }
}
