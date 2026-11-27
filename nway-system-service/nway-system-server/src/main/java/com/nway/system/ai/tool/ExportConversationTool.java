package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.service.AiConversationExportService;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.web.model.AiConversationExportRequest;
import com.nway.system.ai.web.model.AiExportVo;
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

/**
 * 工具：导出当前会话记录。
 * <p>
 * 直接读取 ai_message 生成文件，不重新调用大模型分析；默认导出截至上一轮已落库的消息，
 * 避免把“导出这段对话”这条指令写进文件。
 *
 * @author nway
 */
@Slf4j
public class ExportConversationTool extends ToolBase {

    public static final String NAME = "export_conversation";

    private static final String DESCRIPTION = """
            导出当前或指定会话的记录为文件，不重新调用大模型分析。
            适用：用户要求导出/下载/保存这段对话、聊天记录、会话记录。
            默认导出截至上一轮已落库的消息（不包含本条导出指令），格式 docx。
            内容包含用户问题、助手结论与工具结果表格；默认不含 thinking、页面上下文、原始 JSON。
            会话过长时不会静默截断，而是提示缩小范围（lastN / timeRange）或分卷导出。
            生成成功后只需简短告知用户文件已生成，下载链接由界面展示。
            """;

    private final AiConversationExportService exportService;

    public ExportConversationTool(AiConversationExportService exportService) {
        super(ToolBase.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema())
                .readOnly(true));
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
        // 工具在 reactor 线程执行，读取 ai_message 与写入文件都需要显式恢复租户上下文
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
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        AiConversationExportRequest request;
        try {
            request = JSON.parseObject(JSON.toJSONString(input), AiConversationExportRequest.class);
        } catch (Exception e) {
            return error(AiErrors.INVALID_ARGUMENT, "导出参数格式不正确：" + e.getMessage(), false);
        }
        String conversationId = StringUtils.defaultIfBlank(
                input.get("conversationId") == null ? null : String.valueOf(input.get("conversationId")),
                user.getConversationId());
        try {
            AiExportVo vo = exportService.export(user, conversationId, request);
            log.info("工具调用完成 tool={} session={} toolCallId={} format={} fileId={} size={}",
                    NAME, sessionId, toolCallId, request.getFormat(), vo.getFileId(), vo.getSize());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("fileId", vo.getFileId());
            body.put("fileName", vo.getFileName());
            body.put("size", vo.getSize());
            body.put("format", StringUtils.defaultIfBlank(request.getFormat(), "docx"));
            body.put("url", vo.getUrl());
            body.put("message", "会话已导出，点击对话中的文件名即可下载");
            return ToolResultBlock.text(JSON.toJSONString(body));
        } catch (AiConversationExportService.ConversationTooLargeException e) {
            log.warn("会话导出被拒绝 tool={} session={} toolCallId={} message={}",
                    NAME, sessionId, toolCallId, e.getMessage());
            return error(AiErrors.DATA_TOO_LARGE, e.getMessage(), true);
        } catch (Exception e) {
            log.error("会话导出异常 tool={} session={} toolCallId={}", NAME, sessionId, toolCallId, e);
            return error("EXPORT_FAILED", "导出失败，请稍后重试", false);
        }
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
            node.put("suggestedAction", "NARROW_SCOPE");
            node.put("options", List.of(
                    Map.of("action", "NARROW_SCOPE", "label", "缩小导出范围"),
                    Map.of("action", "MENU_EXPORT", "label", "通过业务菜单导出")));
            body.put("handoff", node);
        }
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private static Map<String, Object> inputSchema() {
        Map<String, Object> rangeProperties = new LinkedHashMap<>();
        rangeProperties.put("start", Map.of("type", "string", "description", "开始日期 yyyy-MM-dd"));
        rangeProperties.put("end", Map.of("type", "string", "description", "结束日期 yyyy-MM-dd"));
        Map<String, Object> timeRange = new LinkedHashMap<>();
        timeRange.put("type", "object");
        timeRange.put("properties", rangeProperties);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("conversationId", Map.of(
                "type", "string",
                "description", "会话 ID，默认当前会话"));
        properties.put("format", Map.of(
                "type", "string",
                "enum", List.of("docx", "xlsx", "html", "pptx"),
                "description", "导出格式，默认 docx"));
        properties.put("scope", Map.of(
                "type", "string",
                "enum", List.of("ALL", "LAST_N", "SELECTED"),
                "description", "导出范围，默认 ALL（截至上一轮）"));
        properties.put("lastN", Map.of(
                "type", "integer",
                "description", "scope=LAST_N 时导出最近 N 条消息"));
        properties.put("messageIds", Map.of(
                "type", "array",
                "description", "scope=SELECTED 时导出指定消息 ID",
                "items", Map.of("type", "string")));
        properties.put("timeRange", timeRange);
        properties.put("includeToolResults", Map.of(
                "type", "boolean",
                "description", "是否包含工具结果表格，默认 true"));
        properties.put("includeAttachments", Map.of(
                "type", "boolean",
                "description", "是否包含附件名称与链接，默认 true"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }
}
