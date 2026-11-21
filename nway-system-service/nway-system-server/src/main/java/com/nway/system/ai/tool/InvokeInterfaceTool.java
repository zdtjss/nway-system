package com.nway.system.ai.tool;

import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.service.AiChatFileService;
import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.service.AiArgumentValidator;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiInvoker;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.service.AiPermissionService;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.domain.AiValidationResult;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具 2：调用任意已配置接口。
 * <p>
 * 写操作通过 {@link #checkPermissions} 返回 ASK，由 AgentScope HITL 弹出确认表单；
 * 用户确认后后端重新校验参数再执行。
 *
 * @author nway
 */
@Slf4j
public class InvokeInterfaceTool extends ToolBase {

    public static final String NAME = "invoke_interface";

    private static final int MAX_RESULT_LENGTH = 8000;

    private final AiInterfaceService interfaceService;
    private final AiArgumentValidator validator;
    private final AiInvoker invoker;
    private final AiAuditService auditService;
    private final AiPermissionService permissionService;
    private final com.nway.system.ai.service.AiChatFileService chatFileService;

    public InvokeInterfaceTool(AiInterfaceService interfaceService, AiArgumentValidator validator,
                               AiInvoker invoker, AiAuditService auditService,
                               AiPermissionService permissionService,
                               com.nway.system.ai.service.AiChatFileService chatFileService) {
        super(ToolBase.builder()
                .name(NAME)
                .description("""
                        调用业务接口。args 必须严格使用 get_interface_config 返回的 inputSchema 字段。
                        如果缺少必填信息，先向用户追问，不要猜测。
                        写操作会触发用户确认，确认通过后才会真正提交。
                        出参以接口 outputSchema 为准：回答只能使用 outputSchema 中定义的字段，不得输出未定义的字段。
                        """)
                .inputSchema(inputSchema())
                .readOnly(false));
        this.interfaceService = interfaceService;
        this.validator = validator;
        this.invoker = invoker;
        this.auditService = auditService;
        this.permissionService = permissionService;
        this.chatFileService = chatFileService;
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> input,
                                                     PermissionContextState context) {
        String interfaceCode = input == null ? null : string(input.get("interfaceCode"));
        AiInterfaceConfig config = interfaceService.getAnyPublishedByCode(interfaceCode);
        if (config != null && config.isWrite()) {
            return Mono.just(PermissionDecision.ask("写操作需要确认：" + config.getName()));
        }
        return Mono.just(PermissionDecision.allow("只读接口"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    private ToolResultBlock execute(ToolCallParam param) {
        long start = System.currentTimeMillis();
        String sessionId = AiLogs.sessionId(param);
        String toolCallId = AiLogs.toolCallId(param);
        AiUserContext user = param.getRuntimeContext() == null
                ? null : param.getRuntimeContext().get(AiUserContext.class);
        if (user == null) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=缺少用户上下文",
                    NAME, sessionId, toolCallId);
            return error(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文", null);
        }
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        String interfaceCode = string(input.get("interfaceCode"));
        if (StringUtils.isBlank(interfaceCode)) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=interfaceCode 为空",
                    NAME, sessionId, toolCallId);
            return error(AiErrors.INVALID_ARGUMENT, "interfaceCode 不能为空", null);
        }
        AiInterfaceConfig config = interfaceService.getPublishedByCode(user.effectiveTenantCode(), interfaceCode);
        if (config == null) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} error=接口不存在或未发布",
                    NAME, sessionId, toolCallId, interfaceCode);
            return error(AiErrors.NO_MATCH, "接口不存在或未发布：" + interfaceCode, interfaceCode);
        }
        AiPermissionService.PermissionDecision permission = permissionService.evaluate(user, config);
        if (!permission.allowed()) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} employeeId={} "
                            + "error=无调用权限 reason={} decisionId={}",
                    NAME, sessionId, toolCallId, interfaceCode, user.getEmployeeId(),
                    permission.reason(), permission.decisionId());
            return error(AiErrors.PERMISSION_DENIED, "当前用户无权调用：" + config.getName(), interfaceCode);
        }

        Object rawArgs = input.get("args");
        Map<String, Object> args = rawArgs instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        AiValidationResult validation = validator.validate(config, args, user);
        if (!validation.isValid()) {
            log.warn("工具调用参数校验失败 tool={} session={} toolCallId={} interfaceCode={} error={} message={}",
                    NAME, sessionId, toolCallId, interfaceCode, validation.getErrorCode(),
                    validation.getMessage());
            return error(validation.getErrorCode(), validation.getMessage(), interfaceCode);
        }
        String fileError = validateFiles(config, validation.getValues(), user);
        if (fileError != null) {
            log.warn("工具调用附件校验失败 tool={} session={} toolCallId={} interfaceCode={} message={}",
                    NAME, sessionId, toolCallId, interfaceCode, fileError);
            return error(AiErrors.INVALID_ARGUMENT, fileError, interfaceCode);
        }

        log.info("工具调用开始 tool={} session={} toolCallId={} interfaceCode={} write={} args={}",
                NAME, sessionId, toolCallId, interfaceCode, config.isWrite(),
                AiLogs.summarizeArgs(validation.getValues()));
        AiInvokeResult result = invoker.invoke(config, validation.getValues(), user);
        auditService.recordInvoke(user, interfaceCode, validation.getValues(), result);
        long elapsed = System.currentTimeMillis() - start;
        if (result.isSuccess()) {
            log.info("工具调用完成 tool={} session={} toolCallId={} interfaceCode={} latency={}ms elapsed={}ms",
                    NAME, sessionId, toolCallId, interfaceCode, result.getLatencyMs(), elapsed);
        } else {
            log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} error={} message={} "
                            + "latency={}ms elapsed={}ms", NAME, sessionId, toolCallId, interfaceCode,
                    result.getErrorCode(), result.getMessage(), result.getLatencyMs(), elapsed);
        }
        if (!result.isSuccess()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("interfaceCode", interfaceCode);
            body.put("error", result.getErrorCode());
            body.put("message", result.getMessage());
            body.put("latencyMs", result.getLatencyMs());
            return ToolResultBlock.text(truncate(JSON.toJSONString(body)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("interfaceCode", interfaceCode);
        body.put("code", result.getCode());
        body.put("message", result.getMessage());
        body.put("data", result.getData());
        body.put("latencyMs", result.getLatencyMs());
        return ToolResultBlock.text(truncate(JSON.toJSONString(body)));
    }

    /**
     * FILE/FILE_ARRAY 字段必须是当前用户上传且存在的 fileId。
     */
    private String validateFiles(AiInterfaceConfig config, Map<String, Object> values, AiUserContext user) {
        if (config.getInputSchema() == null) {
            return null;
        }
        for (com.nway.system.ai.domain.AiFieldDef field : config.getInputSchema()) {
            String type = StringUtils.upperCase(field.getType());
            Object value = values.get(field.getKey());
            if (value == null) {
                continue;
            }
            try {
                if ("FILE".equals(type)) {
                    chatFileService.assertOwned(String.valueOf(value), user);
                } else if ("FILE_ARRAY".equals(type) && value instanceof java.util.Collection<?> files) {
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

    private ToolResultBlock error(String code, String message, String interfaceCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        if (interfaceCode != null) {
            body.put("interfaceCode", interfaceCode);
        }
        body.put("error", code);
        body.put("message", message);
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private String truncate(String json) {
        if (json == null || json.length() <= MAX_RESULT_LENGTH) {
            return json;
        }
        return json.substring(0, MAX_RESULT_LENGTH) + "…[结果已截断]";
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("interfaceCode", Map.of(
                "type", "string",
                "description", "get_interface_config 返回的接口编码"));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("type", "object");
        args.put("description", "接口入参，字段由 inputSchema 决定");
        properties.put("args", args);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("interfaceCode", "args"));
        return schema;
    }
}
