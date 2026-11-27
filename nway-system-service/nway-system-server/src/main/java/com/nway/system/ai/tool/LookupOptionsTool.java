package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.service.AiOptionService;
import com.nway.system.ai.service.AiPermissionService;
import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可选工具：按关键词查询字段候选值。
 * <p>
 * 仅当接口字段选项来自字典/HTTP/SEARCH 且候选量大时使用，避免把全量选项塞进上下文。
 *
 * @author nway
 */
@Slf4j
public class LookupOptionsTool extends ToolBase {

    public static final String NAME = "lookup_options";

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final AiInterfaceService interfaceService;
    private final AiOptionService optionService;
    private final AiPermissionService permissionService;

    public LookupOptionsTool(AiInterfaceService interfaceService, AiOptionService optionService,
                             AiPermissionService permissionService) {
        super(ToolBase.builder()
                .name(NAME)
                .description("""
                        按关键词查询接口字段的候选值（下拉选项）。仅在 search_interfaces 返回的字段
                        带 optionSource 或选项过多时使用；查询结果仍需用户确认后再传给 invoke_interface。
                        """)
                .inputSchema(inputSchema())
                .readOnly(true)
                .concurrencySafe(true));
        this.interfaceService = interfaceService;
        this.optionService = optionService;
        this.permissionService = permissionService;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock execute(ToolCallParam param) {
        long start = System.currentTimeMillis();
        String sessionId = AiLogs.sessionId(param);
        String toolCallId = AiLogs.toolCallId(param);
        AiUserContext user = param.getRuntimeContext() == null
                ? null : param.getRuntimeContext().get(AiUserContext.class);
        if (user == null) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=缺少用户上下文",
                    NAME, sessionId, toolCallId);
            return error(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文");
        }
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        String interfaceCode = string(input.get("interfaceCode"));
        String fieldKey = string(input.get("fieldKey"));
        if (StringUtils.isBlank(interfaceCode) || StringUtils.isBlank(fieldKey)) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=interfaceCode 或 fieldKey 为空",
                    NAME, sessionId, toolCallId);
            return error(AiErrors.INVALID_ARGUMENT, "interfaceCode 与 fieldKey 不能为空");
        }
        AiInterfaceConfig config = interfaceService.getPublishedByCode(user.effectiveTenantCode(), interfaceCode);
        if (config == null) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} error=接口不存在或未发布",
                    NAME, sessionId, toolCallId, interfaceCode);
            return error(AiErrors.NO_MATCH, "接口不存在或未发布：" + interfaceCode);
        }
        AiPermissionService.PermissionDecision permission = permissionService.evaluate(user, config);
        if (!permission.allowed()) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} employeeId={} "
                            + "error=无访问权限 reason={} decisionId={}",
                    NAME, sessionId, toolCallId, interfaceCode, user.getEmployeeId(),
                    permission.reason(), permission.decisionId());
            return error(AiErrors.PERMISSION_DENIED, "当前用户无权访问该接口");
        }
        AiFieldDef field = findField(config.getInputSchema(), fieldKey);
        if (field == null) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} fieldKey={} error=字段不存在",
                    NAME, sessionId, toolCallId, interfaceCode, fieldKey);
            return error(AiErrors.INVALID_ARGUMENT, "字段不存在：" + fieldKey);
        }
        String keyword = string(input.get("keyword"));
        int limit = Math.max(1, Math.min(toInt(input.get("limit"), DEFAULT_LIMIT), MAX_LIMIT));
        List<AiFieldDef.AiOption> options;
        try {
            options = optionService.resolve(field, keyword, user);
        } catch (Exception e) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} fieldKey={} error={}",
                    NAME, sessionId, toolCallId, interfaceCode, fieldKey, e.getMessage());
            return error(AiErrors.OPTION_SOURCE_UNAVAILABLE, e.getMessage());
        }
        if (options == null || options.isEmpty()) {
            log.warn("工具调用无候选值 tool={} session={} toolCallId={} interfaceCode={} fieldKey={} keyword={}",
                    NAME, sessionId, toolCallId, interfaceCode, fieldKey, keyword);
            return error(AiErrors.OPTION_SOURCE_UNAVAILABLE,
                    "字段 " + field.getLabel() + " 未返回候选值，请向用户确认或改用其他关键词");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AiFieldDef.AiOption option : options) {
            if (rows.size() >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", option.getLabel());
            row.put("value", option.getValue());
            rows.add(row);
        }
        log.info("工具调用完成 tool={} session={} toolCallId={} interfaceCode={} fieldKey={} total={} returned={} "
                        + "elapsed={}ms", NAME, sessionId, toolCallId, interfaceCode, fieldKey,
                options.size(), rows.size(), System.currentTimeMillis() - start);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("interfaceCode", interfaceCode);
        body.put("fieldKey", fieldKey);
        body.put("fieldLabel", field.getLabel());
        body.put("total", options.size());
        body.put("options", rows);
        body.put("truncated", options.size() > rows.size());
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private AiFieldDef findField(List<AiFieldDef> fields, String key) {
        if (fields == null) {
            return null;
        }
        for (AiFieldDef field : fields) {
            if (key.equals(field.getKey())) {
                return field;
            }
            AiFieldDef nested = findField(field.getProperties(), key);
            if (nested != null) {
                return nested;
            }
            if (field.getItems() != null) {
                AiFieldDef itemField = findField(field.getItems().getProperties(), key);
                if (itemField != null) {
                    return itemField;
                }
            }
        }
        return null;
    }

    private ToolResultBlock error(String code, String message) {
        return ToolResultBlock.text(JSON.toJSONString(Map.of("error", code, "message", message)));
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("interfaceCode", Map.of(
                "type", "string",
                "description", "接口编码，来自 search_interfaces"));
        properties.put("fieldKey", Map.of(
                "type", "string",
                "description", "需要查询候选值的字段 key"));
        properties.put("keyword", Map.of(
                "type", "string",
                "description", "用户提供的关键词，可为空"));
        properties.put("limit", Map.of(
                "type", "integer",
                "description", "返回条数，默认 20，最大 50"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("interfaceCode", "fieldKey"));
        return schema;
    }
}
