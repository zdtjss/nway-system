package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.service.AiArgumentValidator;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiChatFileService;
import com.nway.system.ai.service.AiDataSize;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiHandoff;
import com.nway.system.ai.service.AiInterfaceCallService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiInvoker;
import com.nway.system.ai.service.AiLimits;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.service.AiPermissionService;
import com.nway.system.ai.service.AiTurnBudget;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具：调用任意已配置接口。
 * <p>
 * 写操作通过 {@link #checkPermissions} 返回 ASK，由 AgentScope HITL 弹出确认表单；
 * 用户确认后后端重新校验参数再执行。
 * <p>
 * 结果进入模型上下文前做体量判定：超过分析上限、单页被截断或本轮预算耗尽时，
 * 返回结构化 handoff，不把数据交给模型。直返查询工具复用同一套调用逻辑，但只做展示裁剪。
 *
 * @author nway
 */
@Slf4j
public class InvokeInterfaceTool extends ToolBase {

    public static final String NAME = "invoke_interface";

    private static final String DESCRIPTION = """
            调用业务接口，并把结果返回给你继续处理。
            适用：分析、解读、对比、总结、统计、趋势、建议、判断；有依赖的多步调用；写操作（会触发用户确认）。
            不适用：用户只想查看数据 → query_interfaces；大数据导出/查询并导出 → export_query_data。
            接口返回的数据保持原样，不要修改、删除或自行精简字段。
            默认不做统计分析：用户未明确要求分析时，不要对结果做汇总统计，也不要复述原始明细。
            只有用户明确要求分析、解读、对比、总结、统计、趋势、建议、判断时，才基于完整结果作答。
            args 必须严格使用 search_interfaces 返回的 inputSchema 字段。
            如果缺少必填信息，先向用户追问，不要猜测。
            出参以接口 outputSchema 为准：回答只能使用 outputSchema 中定义的字段，不得输出未定义的字段。
            结果出现 truncated=true 或 handoff 时，数据不完整：禁止分析、总结、补全或导出，只转述 handoff.message。
            """;

    private final AiInterfaceService interfaceService;
    private final AiInterfaceCallService callService;
    private final AiDataSize dataSize;
    private final AiLimits limits;
    /** true：只读直返查询工具，成功结果直接作为本轮答复，不再进入模型推理。 */
    private final boolean directQuery;

    public InvokeInterfaceTool(AiInterfaceService interfaceService, AiArgumentValidator validator,
                               AiInvoker invoker, AiAuditService auditService,
                               AiPermissionService permissionService,
                               AiChatFileService chatFileService) {
        this(NAME, DESCRIPTION, false, interfaceService,
                new AiInterfaceCallService(interfaceService, validator, invoker, auditService,
                        permissionService, chatFileService),
                new AiDataSize(), new AiLimits());
    }

    public InvokeInterfaceTool(AiInterfaceService interfaceService, AiInterfaceCallService callService,
                               AiDataSize dataSize, AiLimits limits) {
        this(NAME, DESCRIPTION, false, interfaceService, callService, dataSize, limits);
    }

    protected InvokeInterfaceTool(String name, String description, boolean directQuery,
                                  AiInterfaceService interfaceService,
                                  AiArgumentValidator validator, AiInvoker invoker,
                                  AiAuditService auditService, AiPermissionService permissionService,
                                  AiChatFileService chatFileService) {
        this(name, description, directQuery, interfaceService,
                new AiInterfaceCallService(interfaceService, validator, invoker, auditService,
                        permissionService, chatFileService),
                new AiDataSize(), new AiLimits());
    }

    protected InvokeInterfaceTool(String name, String description, boolean directQuery,
                                  AiInterfaceService interfaceService,
                                  AiInterfaceCallService callService,
                                  AiDataSize dataSize, AiLimits limits) {
        super(ToolBase.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema())
                .readOnly(directQuery)
                .returnDirect(directQuery));
        this.interfaceService = interfaceService;
        this.callService = callService;
        this.dataSize = dataSize;
        this.limits = limits;
        this.directQuery = directQuery;
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> input,
                                                     PermissionContextState context) {
        String interfaceCode = input == null ? null : string(input.get("interfaceCode"));
        AiInterfaceConfig config = interfaceService.getAnyPublishedByCode(interfaceCode);
        if (config != null && config.isWrite()) {
            if (directQuery) {
                // 直返查询工具不允许写操作：放行到执行阶段返回可修正的错误，
                // 避免 stopOnReject 直接终止本轮，让模型改用 invoke_interface。
                return Mono.just(PermissionDecision.allow("查询工具拒绝写操作"));
            }
            return Mono.just(PermissionDecision.ask("写操作需要确认：" + config.getName()));
        }
        return Mono.just(PermissionDecision.allow("只读接口"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock execute(ToolCallParam param) {
        String sessionId = AiLogs.sessionId(param);
        String toolCallId = AiLogs.toolCallId(param);
        AiUserContext user = param.getRuntimeContext() == null
                ? null : param.getRuntimeContext().get(AiUserContext.class);
        if (user == null) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=缺少用户上下文",
                    getName(), sessionId, toolCallId);
            return error(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文", null);
        }
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        String interfaceCode = string(input.get("interfaceCode"));
        if (StringUtils.isBlank(interfaceCode)) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=interfaceCode 为空",
                    getName(), sessionId, toolCallId);
            return error(AiErrors.INVALID_ARGUMENT, "interfaceCode 不能为空", null);
        }

        AiInterfaceCallService.CallResult call;
        try {
            call = callService.call(user, interfaceCode, input.get("args"), !directQuery,
                    getName(), sessionId, toolCallId);
        } catch (AiInterfaceCallService.CallException e) {
            return error(e.getErrorCode(), e.getMessage(), interfaceCode);
        }

        if (!call.success()) {
            if (call.truncated()) {
                // 1MB 截断不再当作成功文本返回：直接 handoff，避免模型基于残缺 JSON 编造结论
                return ToolResultBlock.text(JSON.toJSONString(
                        AiHandoff.truncated(interfaceCode, call.rawBytes())));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("interfaceCode", interfaceCode);
            body.put("error", call.errorCode());
            body.put("message", call.message());
            body.put("latencyMs", call.invoke() == null ? 0 : call.invoke().getLatencyMs());
            return ToolResultBlock.text(JSON.toJSONString(body))
                    .withState(ToolResultState.ERROR);
        }

        Object data = call.invoke().getData();
        AiDataSize.Size size = dataSize.measure(data);
        if (directQuery) {
            return directResult(interfaceCode, call, data, size);
        }
        return analysisResult(interfaceCode, call, data, size, param);
    }

    /**
     * 直查展示：超展示上限时只截取前 N 行，保留可渲染数据并明确告警。
     */
    private ToolResultBlock directResult(String interfaceCode, AiInterfaceCallService.CallResult call,
                                         Object data, AiDataSize.Size size) {
        AiDataSize.DisplayData display = dataSize.fitForDisplay(
                data, call.config().getOutputSchema(), limits);
        boolean truncated = display.truncated();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("interfaceCode", interfaceCode);
        body.put("interfaceName", call.interfaceName());
        body.put("code", call.invoke().getCode());
        body.put("message", call.message());
        body.put("data", display.data());
        body.put("rowCount", size.rows());
        if (size.total() != null) {
            body.put("total", size.total());
        }
        body.put("truncated", truncated);
        if (truncated) {
            body.put("warning", "数据量较大，仅展示部分结果；完整数据请通过对应业务菜单导出");
        }
        body.put("latencyMs", call.invoke().getLatencyMs());
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    /**
     * 分析：超过分析上限、单页截断或本轮预算耗尽时返回 handoff，不返回任何数据。
     */
    private ToolResultBlock analysisResult(String interfaceCode, AiInterfaceCallService.CallResult call,
                                           Object data, AiDataSize.Size size, ToolCallParam param) {
        if (call.truncated()) {
            return ToolResultBlock.text(JSON.toJSONString(
                    AiHandoff.truncated(interfaceCode, call.rawBytes())));
        }
        if (dataSize.exceedsAnalysis(size, limits)) {
            log.info("工具结果超过分析上限 interfaceCode={} rows={} chars={} total={} maxRows={} maxChars={} maxTotal={}",
                    interfaceCode, size.rows(), size.chars(), size.total(),
                    limits.getAnalysisMaxRows(), limits.getAnalysisMaxChars(), limits.getAnalysisMaxTotal());
            return ToolResultBlock.text(JSON.toJSONString(
                    AiHandoff.analysisTooLarge(interfaceCode, size, limits)));
        }
        AiTurnBudget budget = param.getRuntimeContext() == null
                ? null : param.getRuntimeContext().get(AiTurnBudget.class);
        if (budget != null && budget.consume(size.chars())) {
            log.info("本轮分析预算耗尽 interfaceCode={} chars={} used={} max={}", interfaceCode,
                    size.chars(), budget.getUsedChars(), budget.getMaxChars());
            return ToolResultBlock.text(JSON.toJSONString(
                    AiHandoff.turnBudgetExceeded(interfaceCode, budget)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("interfaceCode", interfaceCode);
        body.put("interfaceName", call.interfaceName());
        body.put("code", call.invoke().getCode());
        body.put("message", call.message());
        // 接口返回的数据原样保留；是否需要统计/分析由用户问题决定，不修改接口结果
        body.put("data", data);
        body.put("rowCount", size.rows());
        if (size.total() != null) {
            body.put("total", size.total());
        }
        body.put("truncated", false);
        body.put("next", "接口结果已展示；用户未明确要求分析时，不要对数据做统计分析，也不要复述原始明细。");
        body.put("latencyMs", call.invoke().getLatencyMs());
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private ToolResultBlock error(String code, String message, String interfaceCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        if (interfaceCode != null) {
            body.put("interfaceCode", interfaceCode);
        }
        body.put("error", code);
        body.put("message", message);
        return ToolResultBlock.text(JSON.toJSONString(body)).withState(ToolResultState.ERROR);
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("interfaceCode", Map.of(
                "type", "string",
                "description", "search_interfaces 返回的接口编码"));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("type", "object");
        args.put("description", "接口入参，字段由 inputSchema 决定");
        properties.put("args", args);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("interfaceCode", "args"));
        return schema;
    }
}
