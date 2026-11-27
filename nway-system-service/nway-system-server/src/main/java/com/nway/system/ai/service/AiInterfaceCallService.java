package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.domain.AiValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 接口调用编排：接口查询、权限、参数校验、附件归属、审计与调用。
 * <p>
 * 单次调用、批量只读查询与确定性导出共用同一套边界，避免各工具各写一份校验逻辑。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiInterfaceCallService {

    private final AiInterfaceService interfaceService;
    private final AiArgumentValidator validator;
    private final AiInvoker invoker;
    private final AiAuditService auditService;
    private final AiPermissionService permissionService;
    private final AiChatFileService chatFileService;

    public AiInterfaceCallService(AiInterfaceService interfaceService, AiArgumentValidator validator,
                                  AiInvoker invoker, AiAuditService auditService,
                                  AiPermissionService permissionService,
                                  AiChatFileService chatFileService) {
        this.interfaceService = interfaceService;
        this.validator = validator;
        this.invoker = invoker;
        this.auditService = auditService;
        this.permissionService = permissionService;
        this.chatFileService = chatFileService;
    }

    public record CallResult(boolean success, String interfaceCode, String interfaceName,
                             AiInterfaceConfig config, String errorCode, String message,
                             AiInvokeResult invoke, long elapsedMs) {

        public boolean truncated() {
            return invoke != null && invoke.isTruncated();
        }

        public long rawBytes() {
            return invoke == null ? 0 : invoke.getRawBytes();
        }
    }

    /**
     * 可预期的调用失败（接口不存在、无权限、参数非法、附件非法），由调用方转换为工具结果。
     */
    public static class CallException extends RuntimeException {

        private final String errorCode;

        public CallException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * 读取当前租户已发布接口；不存在时抛出可修复错误。
     */
    public AiInterfaceConfig requireConfig(AiUserContext user, String interfaceCode) {
        if (StringUtils.isBlank(interfaceCode)) {
            throw new CallException(AiErrors.INVALID_ARGUMENT, "interfaceCode 不能为空");
        }
        AiInterfaceConfig config = interfaceService.getPublishedByCode(user.effectiveTenantCode(), interfaceCode);
        if (config == null) {
            throw new CallException(AiErrors.NO_MATCH, "接口不存在或未发布：" + interfaceCode);
        }
        return config;
    }

    /**
     * 写操作/权限前置校验：供批量查询在真正执行前整体拒绝写接口。
     */
    public void assertReadable(AiUserContext user, AiInterfaceConfig config) {
        if (config.isWrite()) {
            throw new CallException(AiErrors.PERMISSION_DENIED,
                    "该工具只支持只读查询，写操作请使用 invoke_interface：" + config.getName());
        }
        AiPermissionService.PermissionDecision permission = permissionService.evaluate(user, config);
        if (!permission.allowed()) {
            throw new CallException(AiErrors.PERMISSION_DENIED, "当前用户无权调用：" + config.getName());
        }
    }

    /**
     * 参数与附件校验：返回注入用户上下文后的最终入参。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> validateArgs(AiUserContext user, AiInterfaceConfig config, Object rawArgs) {
        Map<String, Object> args = rawArgs instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        AiValidationResult validation = validator.validate(config, args, user);
        if (!validation.isValid()) {
            throw new CallException(validation.getErrorCode(), validation.getMessage());
        }
        String fileError = validateFiles(config, validation.getValues(), user);
        if (fileError != null) {
            throw new CallException(AiErrors.INVALID_ARGUMENT, fileError);
        }
        return validation.getValues();
    }

    /**
     * 完整调用：校验 + 权限 + 审计 + HTTP 调用。
     *
     * @param allowWrite false 时写接口直接拒绝（查询/导出链路）
     */
    public CallResult call(AiUserContext user, String interfaceCode, Object rawArgs,
                           boolean allowWrite, String toolName, String sessionId, String toolCallId) {
        long start = System.currentTimeMillis();
        if (user == null) {
            throw new CallException(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文");
        }
        AiInterfaceConfig config = requireConfig(user, interfaceCode);
        if (!allowWrite) {
            assertReadable(user, config);
        } else {
            AiPermissionService.PermissionDecision permission = permissionService.evaluate(user, config);
            if (!permission.allowed()) {
                log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} employeeId={} "
                                + "error=无调用权限 reason={} decisionId={}",
                        toolName, sessionId, toolCallId, interfaceCode, user.getEmployeeId(),
                        permission.reason(), permission.decisionId());
                throw new CallException(AiErrors.PERMISSION_DENIED, "当前用户无权调用：" + config.getName());
            }
        }
        Map<String, Object> values;
        try {
            values = validateArgs(user, config, rawArgs);
        } catch (CallException e) {
            log.warn("工具调用参数校验失败 tool={} session={} toolCallId={} interfaceCode={} error={} message={}",
                    toolName, sessionId, toolCallId, interfaceCode, e.getErrorCode(), e.getMessage());
            throw e;
        }
        log.info("工具调用开始 tool={} session={} toolCallId={} interfaceCode={} write={} args={}",
                toolName, sessionId, toolCallId, interfaceCode, config.isWrite(),
                AiLogs.summarizeArgs(values));
        AiInvokeResult result = invoker.invoke(config, values, user);
        auditService.recordInvoke(user, interfaceCode, values, result);
        long elapsed = System.currentTimeMillis() - start;
        if (result.isSuccess()) {
            log.info("工具调用完成 tool={} session={} toolCallId={} interfaceCode={} latency={}ms elapsed={}ms "
                            + "rawBytes={} truncated={}", toolName, sessionId, toolCallId, interfaceCode,
                    result.getLatencyMs(), elapsed, result.getRawBytes(), result.isTruncated());
        } else {
            log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} error={} message={} "
                            + "latency={}ms elapsed={}ms truncated={}", toolName, sessionId, toolCallId,
                    interfaceCode, result.getErrorCode(), result.getMessage(), result.getLatencyMs(),
                    elapsed, result.isTruncated());
        }
        return new CallResult(result.isSuccess(), interfaceCode, config.getName(), config,
                result.getErrorCode(), result.getMessage(), result, elapsed);
    }

    /**
     * FILE/FILE_ARRAY 字段必须是当前用户上传且存在的 fileId。
     */
    private String validateFiles(AiInterfaceConfig config, Map<String, Object> values, AiUserContext user) {
        if (config.getInputSchema() == null || values == null) {
            return null;
        }
        for (AiFieldDef field : config.getInputSchema()) {
            String type = StringUtils.upperCase(field.getType());
            Object value = values.get(field.getKey());
            if (value == null) {
                continue;
            }
            try {
                if ("FILE".equals(type)) {
                    chatFileService.assertOwned(String.valueOf(value), user);
                } else if ("FILE_ARRAY".equals(type) && value instanceof Collection<?> files) {
                    for (Object fileId : files) {
                        chatFileService.assertOwned(String.valueOf(fileId), user);
                    }
                }
            } catch (Exception e) {
                return "附件字段 " + field.getLabel() + " 的 fileId 无效：" + e.getMessage();
            }
        }
        return null;
    }
}
