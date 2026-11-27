package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.service.AiConversationExportService;
import com.nway.system.ai.service.AiConversationHistoryReader;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.web.model.AiExportVo;
import com.nway.system.ai.web.model.AiMessageVo;
import com.nway.system.common.tenant.TenantUtil;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具：导出当前会话中已有的查询结果，不重新调用接口、不重新分析。
 * <p>
 * 原结果被截断或 handoff 时拒绝生成文件；完整结果按表格导出。
 *
 * @author nway
 */
@Slf4j
public class ExportToolResultTool extends ToolBase {

    public static final String NAME = "export_tool_result";

    private static final String DESCRIPTION = """
            导出当前会话中已经查询到的结果，不重新调用接口、不重新分析。
            适用：用户要求导出/下载“刚才的查询结果”“上面的数据”。
            默认取当前会话最近一次成功的查询/调用结果；也可用 toolCallId 精确指定。
            原结果被截断或超出在线处理上限时不会生成文件，只返回 handoff 提示。
            不适用：导出整段对话 → export_conversation；大数据全量导出 → export_query_data。
            """;

    /** 可作为导出源的查询/调用工具 */
    private static final Set<String> SOURCE_TOOLS = Set.of(
            "query_interface", "query_interfaces", "invoke_interface");

    private final AiConversationHistoryReader historyReader;
    private final AiConversationExportService exportService;

    public ExportToolResultTool(AiConversationHistoryReader historyReader,
                                AiConversationExportService exportService) {
        super(ToolBase.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema())
                .readOnly(true));
        this.historyReader = historyReader;
        this.exportService = exportService;
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
            return error(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文", true);
        }
        // 工具在 reactor 线程执行，读取历史消息与写入文件都需要显式恢复租户上下文
        String tenant = user.effectiveTenantCode();
        if (StringUtils.isBlank(tenant)) {
            return executeInTenant(param, user);
        }
        Object[] holder = new Object[1];
        TenantUtil.runAsTenant(tenant, () -> holder[0] = executeInTenant(param, user));
        return (ToolResultBlock) holder[0];
    }

    private ToolResultBlock executeInTenant(ToolCallParam param, AiUserContext user) {
        String sessionId = AiLogs.sessionId(param);
        String toolCallId = AiLogs.toolCallId(param);
        if (StringUtils.isBlank(user.getConversationId())) {
            return error(AiErrors.INVALID_ARGUMENT, "当前没有可导出的会话结果", false);
        }
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        String targetId = string(input.get("toolCallId"));
        String format = StringUtils.defaultIfBlank(string(input.get("format")), "xlsx");
        String title = string(input.get("title"));

        List<AiMessageVo> messages;
        try {
            messages = historyReader.list(user, user.getConversationId());
        } catch (Exception e) {
            return error(AiErrors.INVALID_ARGUMENT, e.getMessage(), false);
        }
        Map<String, Object> block = findToolBlock(messages, targetId);
        if (block == null) {
            return error(AiErrors.NO_MATCH, targetId == null
                    ? "当前会话中没有可导出的查询结果"
                    : "未找到指定的查询结果：" + targetId, false);
        }
        try {
            AiExportVo vo = exportService.exportToolResult(user, block, format, title);
            log.info("工具调用完成 tool={} session={} toolCallId={} sourceCallId={} format={} fileId={}",
                    NAME, sessionId, toolCallId, block.get("id"), format, vo.getFileId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("fileId", vo.getFileId());
            body.put("fileName", vo.getFileName());
            body.put("size", vo.getSize());
            body.put("format", format);
            body.put("url", vo.getUrl());
            body.put("message", "结果已导出，点击对话中的文件名即可下载");
            return ToolResultBlock.text(JSON.toJSONString(body));
        } catch (AiConversationExportService.ConversationTooLargeException e) {
            log.warn("已有结果导出被拒绝 tool={} session={} message={}", NAME, sessionId, e.getMessage());
            return error(AiErrors.DATA_TOO_LARGE, e.getMessage(), true);
        } catch (Exception e) {
            log.error("已有结果导出异常 tool={} session={}", NAME, sessionId, e);
            return error("EXPORT_FAILED", "导出失败，请稍后重试", false);
        }
    }

    /**
     * 从后往前找最近一次查询/调用结果；指定 toolCallId 时精确匹配。
     */
    private Map<String, Object> findToolBlock(List<AiMessageVo> messages, String toolCallId) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessageVo message = messages.get(i);
            List<Map<String, Object>> blocks = message.getBlocks();
            if (blocks == null) {
                continue;
            }
            for (int j = blocks.size() - 1; j >= 0; j--) {
                Map<String, Object> block = blocks.get(j);
                if (!"tool_use".equals(block.get("type"))
                        || !SOURCE_TOOLS.contains(String.valueOf(block.get("name")))) {
                    continue;
                }
                if (!(block.get("result") instanceof String result) || StringUtils.isBlank(result)) {
                    continue;
                }
                if (toolCallId != null && !toolCallId.equals(block.get("id"))) {
                    continue;
                }
                return block;
            }
        }
        return null;
    }

    private ToolResultBlock error(String code, String message, boolean handoff) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", code);
        body.put("message", message);
        if (handoff) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "MENU_OR_ADMIN");
            node.put("message", message);
            node.put("suggestedAction", "MENU_EXPORT");
            node.put("options", List.of(
                    Map.of("action", "EXPORT_DATA", "label", "导出原始数据"),
                    Map.of("action", "NARROW_SCOPE", "label", "缩小查询范围")));
            body.put("handoff", node);
        }
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("toolCallId", Map.of(
                "type", "string",
                "description", "指定要导出的工具调用 ID；不传则取最近一次结果"));
        properties.put("latest", Map.of(
                "type", "boolean",
                "description", "未指定 toolCallId 时是否取最近一次结果，默认 true"));
        properties.put("format", Map.of(
                "type", "string",
                "enum", List.of("xlsx", "docx", "html"),
                "description", "导出格式，默认 xlsx"));
        properties.put("title", Map.of(
                "type", "string",
                "description", "可选文件标题"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }
}
