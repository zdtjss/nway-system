package com.nway.system.openplatform.web.admin;

import com.nway.system.openplatform.web.model.OpenApiLogVo;
import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.openplatform.service.OpenApiLogService;
import com.nway.system.common.web.Result;
import com.nway.system.openplatform.entity.SysOpenApiLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 开放平台调用日志 Controller（内部管理后台）。
 *
 * @author nway
 */
@RequiresPermission(resource = "open_api_log")
@RestController
@RequestMapping("openApiLog")
public class OpenApiLogController {

    @Autowired
    private OpenApiLogService openApiLogService;

    /**
     * 按应用查询调用日志。
     */
    @GetMapping("queryByApp")
    public Result<List<com.nway.system.openplatform.web.model.OpenApiLogVo>> queryByApp(String appCode) {
        return Result.ok(openApiLogService.queryByApp(appCode));
    }

    /**
     * 查询全部调用日志。
     */
    @GetMapping("queryAll")
    public Result<List<com.nway.system.openplatform.web.model.OpenApiLogVo>> queryAll() {
        return Result.ok(openApiLogService.queryAll());
    }

    /**
     * 按应用维度统计。
     */
    @GetMapping("statsByApp")
    public Result<List<Map<String, Object>>> statsByApp() {
        return Result.ok(openApiLogService.statsByApp());
    }

    /**
     * 按接口维度统计。
     */
    @GetMapping("statsByApi")
    public Result<List<Map<String, Object>>> statsByApi() {
        return Result.ok(openApiLogService.statsByApi());
    }
}
