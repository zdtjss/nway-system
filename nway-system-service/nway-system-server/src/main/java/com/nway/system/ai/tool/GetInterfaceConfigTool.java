package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiInterfaceSearchService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.service.AiPermissionService;
import com.nway.system.ai.service.AiSchemaCompiler;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiOutputField;
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
import java.util.stream.Collectors;

/**
 * 工具 1：按意图/编码读取接口配置与参数 Schema。
 * <p>
 * 只返回模型的必要信息，不暴露 invoke_config 内部请求头与密钥。
 *
 * @author nway
 */
@Slf4j
public class GetInterfaceConfigTool extends ToolBase {

    public static final String NAME = "get_interface_config";

    private final AiInterfaceService interfaceService;
    private final AiInterfaceSearchService searchService;
    private final AiPermissionService permissionService;
    private final AiSchemaCompiler schemaCompiler;

    public GetInterfaceConfigTool(AiInterfaceService interfaceService,
                                  AiInterfaceSearchService searchService,
                                  AiPermissionService permissionService,
                                  AiSchemaCompiler schemaCompiler) {
        super(ToolBase.builder()
                .name(NAME)
                .description("""
                        查询业务接口配置。二选一：
                        1) query：直接传用户原始意图（不要改写成接口名），系统会做规则+语义检索；
                        2) interfaceCode：精确读取某个接口的完整入参/出参定义。
                        返回的 inputSchema 决定 invoke_interface 的 args 字段；不要臆造字段。
                        """)
                .inputSchema(inputSchema())
                .readOnly(true)
                .concurrencySafe(true));
        this.interfaceService = interfaceService;
        this.searchService = searchService;
        this.permissionService = permissionService;
        this.schemaCompiler = schemaCompiler;
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
        AiUserContext user = param.getRuntimeContext() == null ? null : param.getRuntimeContext().get(AiUserContext.class);
        if (user == null) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=缺少用户上下文",
                    NAME, sessionId, toolCallId);
            return error(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文");
        }
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        String interfaceCode = string(input.get("interfaceCode"));
        if (StringUtils.isNotBlank(interfaceCode)) {
            AiInterfaceConfig config = interfaceService.getPublishedByCode(user.effectiveTenantCode(), interfaceCode);
            AiPermissionService.PermissionDecision permission = config == null ? null : permissionService.evaluate(user, config);
            if (config == null || !permission.allowed()) {
                log.warn("工具调用失败 tool={} session={} toolCallId={} interfaceCode={} error=接口不存在或无权限 "
                                + "reason={} decisionId={}",
                        NAME, sessionId, toolCallId, interfaceCode,
                        permission == null ? "no-interface" : permission.reason(),
                        permission == null ? null : permission.decisionId());
                return error(AiErrors.NO_MATCH, "未找到可用接口：" + interfaceCode);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("candidates", List.of(candidate(config, 1.0)));
            body.put("next", "调用 invoke_interface(interfaceCode, args)，args 只包含 inputSchema 中的字段；回答只使用 outputSchema 定义的字段");
            log.info("工具调用完成 tool={} session={} toolCallId={} interfaceCode={} candidates=1 elapsed={}ms",
                    NAME, sessionId, toolCallId, interfaceCode, System.currentTimeMillis() - start);
            return ToolResultBlock.text(JSON.toJSONString(body));
        }

        String query = string(input.get("query"));
        if (StringUtils.isBlank(query)) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=query 与 interfaceCode 均未提供",
                    NAME, sessionId, toolCallId);
            return error(AiErrors.INVALID_ARGUMENT, "query 与 interfaceCode 必须提供一个");
        }
        int topK = toInt(input.get("topK"), 5);
        String appCode = string(input.get("appCode"));
        // 模型可能把用户原话翻译或改写，检索时补上本轮原话，保证中文业务词仍能命中
        String searchText = searchText(user.getCurrentMessage(), query);
        log.debug("工具调用参数 tool={} session={} toolCallId={} query={} searchText={} appCode={} topK={}",
                NAME, sessionId, toolCallId, AiLogs.truncate(query, 120),
                AiLogs.truncate(searchText, 200), appCode, topK);
        List<AiInterfaceSearchService.ScoredConfig> results = searchService.search(user, searchText, appCode, topK);
        if (results.isEmpty()) {
            log.warn("工具调用无匹配 tool={} session={} toolCallId={} query={}", NAME, sessionId,
                    toolCallId, AiLogs.truncate(searchText, 120));
            return error(AiErrors.NO_MATCH, "未找到与该意图匹配的接口，请换一种描述或向用户确认");
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (AiInterfaceSearchService.ScoredConfig scored : results) {
            candidates.add(candidate(scored.config(), scored.score()));
        }
        // 低于阈值说明只是弱命中：允许模型看到候选，但禁止直接调用，避免拿无关接口编造结论
        boolean lowConfidence = results.get(0).score() < AiInterfaceSearchService.CONFIDENT_SCORE;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidates", candidates);
        if (lowConfidence) {
            body.put("lowConfidence", true);
            body.put("next", "候选置信度低，不要调用 invoke_interface；先向用户确认业务对象，或说明暂未找到对应接口");
        } else {
            body.put("next", "选择最匹配的 interfaceCode，补充用户已明确的信息后调用 invoke_interface；缺失必填字段时先向用户追问");
        }
        log.info("工具调用完成 tool={} session={} toolCallId={} query={} candidates={} top={} lowConfidence={} elapsed={}ms",
                NAME, sessionId, toolCallId, AiLogs.truncate(searchText, 120), candidates.size(),
                topCandidates(results), lowConfidence, System.currentTimeMillis() - start);
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    /**
     * 原话与模型 query 合并检索；两者相同或原话为空时只保留一个。
     */
    private String searchText(String currentMessage, String query) {
        String current = StringUtils.trimToEmpty(currentMessage);
        if (current.isEmpty() || current.equals(query)) {
            return query;
        }
        return current + " " + query;
    }

    /**
     * 记录搜索命中的前几个接口及分值，便于排查 Agent 选错接口。
     */
    private String topCandidates(List<AiInterfaceSearchService.ScoredConfig> results) {
        return results.stream().limit(3)
                .map(scored -> scored.config().getInterfaceCode() + ":"
                        + Math.round(scored.score() * 100) / 100.0)
                .collect(Collectors.joining(","));
    }

    private Map<String, Object> candidate(AiInterfaceConfig config, double score) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("interfaceCode", config.getInterfaceCode());
        node.put("name", config.getName());
        node.put("description", config.getDescription());
        node.put("appCode", config.getAppCode());
        node.put("riskLevel", config.getRiskLevel());
        node.put("confirmRequired", Boolean.TRUE.equals(config.getConfirmRequired()));
        node.put("inputSchema", schemaCompiler.modelFields(config));
        if (config.getInputSchema() == null || config.getInputSchema().isEmpty()) {
            node.put("inputSchemaNote", "未声明参数，args 支持任意字段，将原样转发");
        }
        if (config.getOutputSchema() != null) {
            node.put("outputSchema", config.getOutputSchema().stream().map(this::outputField).toList());
        }
        if (config.getExamples() != null && !config.getExamples().isEmpty()) {
            node.put("examples", config.getExamples());
        }
        node.put("score", Math.round(score * 100) / 100.0);
        return node;
    }

    private Map<String, Object> outputField(AiOutputField field) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("key", field.getKey());
        node.put("label", field.getLabel());
        node.put("type", field.getType());
        node.put("description", field.getDescription());
        if (Boolean.TRUE.equals(field.getSensitive())) {
            node.put("sensitive", true);
        }
        if (field.getProperties() != null && !field.getProperties().isEmpty()) {
            node.put("properties", field.getProperties().stream().map(this::outputField).toList());
        }
        if (field.getItems() != null) {
            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", field.getItems().getType());
            items.put("description", field.getItems().getDescription());
            if (field.getItems().getProperties() != null && !field.getItems().getProperties().isEmpty()) {
                items.put("properties", field.getItems().getProperties().stream().map(this::outputField).toList());
            }
            node.put("items", items);
        }
        return node;
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
        properties.put("query", Map.of(
                "type", "string",
                "description", "用户的原始自然语言意图，例如「提交访厂申请、查询待办」，不要改写成接口名"));
        properties.put("interfaceCode", Map.of(
                "type", "string",
                "description", "已知的接口编码，精确读取配置"));
        properties.put("appCode", Map.of(
                "type", "string",
                "description", "限定目标应用，如 bpm"));
        properties.put("topK", Map.of(
                "type", "integer",
                "description", "候选数量，默认 5，最大 10"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }
}
