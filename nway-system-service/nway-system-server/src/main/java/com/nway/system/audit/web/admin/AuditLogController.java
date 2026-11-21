package com.nway.system.audit.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.audit.service.AuditLogService;
import com.nway.system.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 审计日志查询（管理后台）。
 *
 * @author nway
 */
@RequiresPermission(resource = "audit_log")
@RestController
@RequestMapping("auditLog")
public class AuditLogController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 分页查询审计日志。
     * <p>
     * 参数：operatorId, operationType, apiPath, startTime, endTime, page, pageSize
     */
    @PostMapping("queryPage")
    public Result<Map<String, Object>> queryPage(@RequestBody Map<String, Object> params) {
        String operatorId = getStr(params, "operatorId");
        String operationType = getStr(params, "operationType");
        String apiPath = getStr(params, "apiPath");
        LocalDateTime startTime = parseDateTime(getStr(params, "startTime"));
        LocalDateTime endTime = parseDateTime(getStr(params, "endTime"));
        int page = getInt(params, "page", 1);
        int pageSize = getInt(params, "pageSize", 15);
        return auditLogService.queryPage(operatorId, operationType, apiPath,
                startTime, endTime, page, pageSize);
    }

    private static String getStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) {
            return null;
        }
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static int getInt(Map<String, Object> params, String key, int defaultValue) {
        Object val = params.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static LocalDateTime parseDateTime(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        if (str.contains("T")) {
            return LocalDateTime.parse(str);
        }
        return LocalDateTime.parse(str, DATE_TIME_FORMATTER);
    }
}
