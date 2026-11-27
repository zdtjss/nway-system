package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.audit.dao.AuditLogDao;
import com.nway.system.audit.entity.AuditLog;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI 接口调用审计：复用 sys_audit_log，不新建 ai_tool_call。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiAuditService {

    private final AuditLogDao auditLogDao;

    public AiAuditService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public void recordInvoke(AiUserContext user, String interfaceCode, Map<String, Object> args,
                             AiInvokeResult result) {
        AuditLog entity = base(user, "AI_INVOKE_INTERFACE", "ai_interface:" + interfaceCode);
        entity.setRequestParams(summarize(args));
        entity.setResponseStatus(result != null && result.isSuccess() ? 200 : 500);
        entity.setDurationMs(result == null ? null : (int) result.getLatencyMs());
        insert(entity);
    }

    public void recordConfirm(AiUserContext user, String interfaceCode, boolean confirmed) {
        AuditLog entity = base(user, confirmed ? "AI_CONFIRM_INTERFACE" : "AI_REJECT_INTERFACE",
                "ai_interface:" + interfaceCode);
        entity.setResponseStatus(confirmed ? 200 : 403);
        insert(entity);
    }

    /**
     * 数据导出审计：记录每个查询的接口、行数、页数与完整性。
     */
    public void recordDataExport(AiUserContext user, Map<String, Object> summary) {
        AuditLog entity = base(user, "AI_EXPORT_DATA", "ai_export:data");
        entity.setRequestParams(AiLogs.truncate(JSON.toJSONString(summary), 2000));
        entity.setResponseStatus(200);
        insert(entity);
    }

    private AuditLog base(AiUserContext user, String operationType, String apiPath) {
        AuditLog entity = new AuditLog();
        entity.setOperationType(operationType);
        entity.setApiPath(apiPath);
        entity.setHttpMethod("POST");
        if (user != null) {
            entity.setOperatorId(user.getEmployeeId());
            entity.setOperatorName(user.getEmployeeName());
            entity.setTenantCode(user.getTenantCode());
        }
        return entity;
    }

    private void insert(AuditLog entity) {
        try {
            auditLogDao.insert(entity);
        } catch (Exception e) {
            // 审计失败不影响主流程
            log.warn("AI 审计写入失败 action={} path={} error={}", entity.getOperationType(),
                    entity.getApiPath(), e.getMessage());
        }
    }

    private String summarize(Map<String, Object> args) {
        return AiLogs.summarizeArgs(args);
    }
}
