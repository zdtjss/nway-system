package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.service.AiDataSize;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiInterfaceCallService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiLimits;
import com.nway.system.ai.service.AiLogs;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
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
import java.util.regex.Pattern;

/**
 * 工具：批量只读查询直返。
 * <p>
 * 一次工具调用执行多个只读接口，结果直接作为本轮答复展示，不经过模型分析。
 * 任一接口为写操作时整批拒绝，提示改用 invoke_interface；单个调用失败不影响其他调用。
 *
 * @author nway
 */
@Slf4j
public class QueryInterfacesTool extends ToolBase {

    public static final String NAME = "query_interfaces";

    /**
     * 导出意图兜底：命中时拒绝直返查询。
     * <p>
     * 直返查询成功即结束本轮，模型没有机会再生成文件；导出意图应直接走确定性导出工具。
     */
    private static final Pattern EXPORT_INTENT = Pattern.compile(
            "导出|下载|另存为|保存为|输出为|转成|转换成|生成.{0,8}(文件|报告|表格|excel|word|ppt|html|pdf)",
            Pattern.CASE_INSENSITIVE);

    private static final String DESCRIPTION_TEMPLATE = """
            批量只读查询业务接口，并把结果直接展示给用户；这是只读查询的默认工具。
            适用：一次要查一个或多个互不依赖的只读接口（如同时查询请假和加班），用户只想查看数据，不需要分析。
            结果直接展示，不做统计汇总；不要为了汇总、统计接口结果而改用 invoke_interface。
            不适用：需要分析/解读/对比；需要基于上一个结果继续调用；写操作；导出/下载/保存文件。
            只读接口专属：任一调用命中写接口时整批拒绝，写操作请使用 invoke_interface（会触发用户确认）。
            args 必须严格使用 search_interfaces 返回的 inputSchema 字段；缺少必填信息先向用户追问。
            最多 %d 个调用；单个调用失败不影响其他结果。
            调用成功后本轮直接结束，结果不会返回给你继续推理。
            """;

    private final AiInterfaceCallService callService;
    private final AiInterfaceService interfaceService;
    private final AiDataSize dataSize;
    private final AiLimits limits;

    public QueryInterfacesTool(AiInterfaceCallService callService, AiInterfaceService interfaceService,
                               AiDataSize dataSize, AiLimits limits) {
        super(ToolBase.builder()
                .name(NAME)
                .description(DESCRIPTION_TEMPLATE.formatted(limits.getQueryBatchMaxCalls()))
                .inputSchema(inputSchema(limits.getQueryBatchMaxCalls()))
                .readOnly(true)
                .returnDirect(true));
        this.callService = callService;
        this.interfaceService = interfaceService;
        this.dataSize = dataSize;
        this.limits = limits;
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> input,
                                                     PermissionContextState context) {
        // 只读工具：写接口在执行阶段整体拒绝，便于模型改用 invoke_interface
        return Mono.just(PermissionDecision.allow("只读批量查询"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    private ToolResultBlock execute(ToolCallParam param) {
        String sessionId = AiLogs.sessionId(param);
        String toolCallId = AiLogs.toolCallId(param);
        AiUserContext user = param.getRuntimeContext() == null
                ? null : param.getRuntimeContext().get(AiUserContext.class);
        if (user == null) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=缺少用户上下文",
                    NAME, sessionId, toolCallId);
            return error(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文");
        }
        if (StringUtils.isNotBlank(user.getCurrentMessage())
                && EXPORT_INTENT.matcher(user.getCurrentMessage()).find()) {
            return error(AiErrors.INVALID_ARGUMENT,
                    "本轮用户要求导出文件，query_interfaces 会直接结束本轮导致无法生成文件；"
                            + "请改用 export_query_data（原始数据）、export_tool_result（已有结果）"
                            + "或 export_conversation（对话记录）");
        }
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        Object rawCalls = input.get("calls");
        if (!(rawCalls instanceof List<?> list) || list.isEmpty()) {
            return error(AiErrors.INVALID_ARGUMENT, "calls 不能为空");
        }
        if (list.size() > limits.getQueryBatchMaxCalls()) {
            return error(AiErrors.INVALID_ARGUMENT,
                    "一次最多查询 " + limits.getQueryBatchMaxCalls() + " 个接口");
        }

        List<Call> calls = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> map)) {
                return error(AiErrors.INVALID_ARGUMENT, "calls[" + i + "] 必须为对象");
            }
            Map<String, Object> node = (Map<String, Object>) map;
            String interfaceCode = string(node.get("interfaceCode"));
            if (StringUtils.isBlank(interfaceCode)) {
                return error(AiErrors.INVALID_ARGUMENT, "calls[" + i + "].interfaceCode 不能为空");
            }
            calls.add(new Call(interfaceCode, string(node.get("label")), node.get("args")));
        }

        // 写接口整批拒绝：避免只读工具绕过确认流程
        for (Call call : calls) {
            AiInterfaceConfig config = interfaceService.getPublishedByCode(
                    user.effectiveTenantCode(), call.interfaceCode());
            if (config != null && config.isWrite()) {
                log.warn("批量查询拒绝写接口 tool={} session={} toolCallId={} interfaceCode={}",
                        NAME, sessionId, toolCallId, call.interfaceCode());
                return error(AiErrors.PERMISSION_DENIED,
                        "批量查询只支持只读接口，包含写接口：" + config.getName()
                                + "；写操作请使用 invoke_interface");
            }
        }

        log.info("工具调用开始 tool={} session={} toolCallId={} calls={} interfaces={}",
                NAME, sessionId, toolCallId, calls.size(),
                calls.stream().map(Call::interfaceCode).toList());

        List<Map<String, Object>> results = new ArrayList<>(calls.size());
        boolean anyTruncated = false;
        for (Call call : calls) {
            Map<String, Object> item = queryOne(user, call, sessionId, toolCallId);
            if (Boolean.TRUE.equals(item.get("truncated"))) {
                anyTruncated = true;
            }
            results.add(item);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("results", results);
        body.put("truncated", anyTruncated);
        body.put("next", "查询结果已直接展示给用户，本轮结束；不要对结果做统计汇总或分析。");
        log.info("工具调用完成 tool={} session={} toolCallId={} calls={} failed={} truncated={}",
                NAME, sessionId, toolCallId, calls.size(),
                results.stream().filter(r -> Boolean.FALSE.equals(r.get("success"))).count(),
                anyTruncated);
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private Map<String, Object> queryOne(AiUserContext user, Call call, String sessionId, String toolCallId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("interfaceCode", call.interfaceCode());
        if (StringUtils.isNotBlank(call.label())) {
            item.put("label", call.label());
        }
        AiInterfaceCallService.CallResult result;
        try {
            result = callService.call(user, call.interfaceCode(), call.args(), false,
                    NAME, sessionId, toolCallId);
        } catch (AiInterfaceCallService.CallException e) {
            item.put("success", false);
            item.put("error", e.getErrorCode());
            item.put("message", e.getMessage());
            return item;
        }
        if (!result.success()) {
            item.put("success", false);
            item.put("error", result.errorCode());
            item.put("message", result.message());
            if (result.truncated()) {
                item.put("truncated", true);
                item.put("message", "接口单次返回数据过大，无法保证完整；请通过对应业务菜单导出，或缩小查询范围");
            }
            return item;
        }

        Object data = result.invoke().getData();
        AiDataSize.Size size = dataSize.measure(data);
        AiDataSize.DisplayData display = dataSize.fitForDisplay(
                data, result.config().getOutputSchema(), limits);
        boolean truncated = display.truncated();
        item.put("success", true);
        item.put("interfaceName", result.interfaceName());
        item.put("rowCount", size.rows());
        if (size.total() != null) {
            item.put("total", size.total());
        }
        item.put("truncated", truncated);
        item.put("data", display.data());
        item.put("latencyMs", result.invoke().getLatencyMs());
        if (truncated) {
            item.put("warning", "数据量较大，仅展示部分结果；完整数据请通过对应业务菜单导出");
        }
        return item;
    }

    private ToolResultBlock error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", code);
        body.put("message", message);
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record Call(String interfaceCode, String label, Object args) {
    }

    private static Map<String, Object> inputSchema(int maxCalls) {
        Map<String, Object> callProperties = new LinkedHashMap<>();
        callProperties.put("interfaceCode", Map.of(
                "type", "string",
                "description", "search_interfaces 返回的只读接口编码"));
        callProperties.put("args", Map.of(
                "type", "object",
                "description", "接口入参，字段由 inputSchema 决定"));
        callProperties.put("label", Map.of(
                "type", "string",
                "description", "本次查询的展示名称，如「请假」「加班」"));

        Map<String, Object> call = new LinkedHashMap<>();
        call.put("type", "object");
        call.put("properties", callProperties);
        call.put("required", List.of("interfaceCode", "args"));

        Map<String, Object> calls = new LinkedHashMap<>();
        calls.put("type", "array");
        calls.put("description", "要执行的只读查询，最多 " + maxCalls + " 个");
        calls.put("maxItems", maxCalls);
        calls.put("items", call);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("calls", calls);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("calls"));
        return schema;
    }
}
