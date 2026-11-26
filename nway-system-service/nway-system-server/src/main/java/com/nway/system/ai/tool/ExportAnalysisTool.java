package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiExportService;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.web.model.AiExportRequest;
import com.nway.system.ai.web.model.AiExportVo;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
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
 * 工具：把当前对话中的分析结果导出为 Word / Excel / PPT / HTML 文件。
 * <p>
 * 由模型识别用户的导出意图后调用：模型按当前对话中已展示的真实数据组织 blocks，
 * 服务端只负责格式转换、存储与下载票据，避免前端按钮式导出绕过对话意图。
 *
 * @author nway
 */
@Slf4j
public class ExportAnalysisTool extends ToolBase {

    public static final String NAME = "export_analysis";

    private static final String DESCRIPTION = """
            把当前对话中的分析结果导出为文件，并返回用户可下载的文件信息。
            仅当用户明确要求导出、下载、保存分析结果或生成报告/表格文件时调用；普通问答不要调用。
            只能使用当前对话中已经展示或工具返回的真实数据，禁止编造、推测或补全不存在的记录；
            数据不足时先向用户说明缺少什么，不要生成空文件。
            blocks 按展示顺序组织：
            - 正文：{"type":"text","text":"Markdown 正文"}；
            - 表格、指标卡、图表、详情、清单、文件：{"type":"view","view":{...}}，
              view 的结构与本轮正文中的 nway-view JSON 完全一致。
            示例：{"format":"xlsx","title":"区域销售","blocks":[
              {"type":"text","text":"## 结论"},
              {"type":"view","view":{"type":"table","columns":[{"key":"region","label":"区域"}],
               "rows":[{"region":"华东"}]}}]}
            格式选择：用户指定时按用户要求；未指定时，明细/表格优先 xlsx，分析报告优先 docx，
            汇报演示优先 pptx，需要浏览器直接查看时用 html。
            导出所需数据不在当前对话中时，必须先用 invoke_interface 查询（禁止使用 query_interface，
            它会直接结束本轮），拿到结果后再调用本工具。
            用户明确要求导出时，必须实际调用本工具生成文件；只查询数据或在正文中说明导出计划都不算完成。
            生成成功后只需简短告知用户文件已生成，下载链接由对话界面展示，不要重复粘贴大段数据。
            """;

    private final AiExportService exportService;

    public ExportAnalysisTool(AiExportService exportService) {
        super(ToolBase.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema())
                .readOnly(true)
                .concurrencySafe(true));
        this.exportService = exportService;
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
        String format = string(input.get("format"));
        Object rawBlocks = input.get("blocks");
        if (StringUtils.isBlank(format)) {
            return error(AiErrors.INVALID_ARGUMENT, "format 不能为空，可选 docx、pptx、xlsx、html");
        }
        List<Object> blocks = normalizeBlocks(rawBlocks);
        if (blocks == null) {
            return error(AiErrors.INVALID_ARGUMENT, "blocks 不能为空，请提供要导出的分析内容");
        }
        try {
            AiExportRequest request = new AiExportRequest();
            request.setFormat(format);
            request.setTitle(string(input.get("title")));
            request.setConversationId(user.getConversationId());
            request.setBlocks(JSON.parseArray(JSON.toJSONString(blocks), AiExportRequest.Block.class));

            Object[] holder = new Object[1];
            String tenant = user.effectiveTenantCode();
            Runnable action = () -> holder[0] = exportService.export(request);
            if (StringUtils.isBlank(tenant)) {
                action.run();
            } else {
                // 工具在 reactor 线程执行，需显式恢复租户上下文，文件才能写入用户租户
                TenantUtil.runAsTenant(tenant, action);
            }
            AiExportVo vo = (AiExportVo) holder[0];
            log.info("工具调用完成 tool={} session={} toolCallId={} format={} fileId={} size={} elapsed={}ms",
                    NAME, sessionId, toolCallId, format, vo.getFileId(), vo.getSize(),
                    System.currentTimeMillis() - start);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("fileId", vo.getFileId());
            body.put("fileName", vo.getFileName());
            body.put("size", vo.getSize());
            body.put("format", format);
            body.put("url", vo.getUrl());
            body.put("message", "文件已生成，点击对话中的文件名即可下载");
            return ToolResultBlock.text(JSON.toJSONString(body));
        } catch (NwaySystemException e) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error={}",
                    NAME, sessionId, toolCallId, e.getMessage());
            return error("EXPORT_FAILED", e.getMessage());
        } catch (Exception e) {
            log.error("工具调用异常 tool={} session={} toolCallId={}", NAME, sessionId, toolCallId, e);
            return error("EXPORT_FAILED", "导出失败，请稍后重试");
        }
    }

    private ToolResultBlock error(String code, String message) {
        return ToolResultBlock.text(JSON.toJSONString(Map.of("error", code, "message", message)));
    }

    /**
     * 兼容模型把 blocks 或 block.view 二次序列化为字符串的写法，避免整个导出因格式问题失败。
     */
    private List<Object> normalizeBlocks(Object raw) {
        Object value = raw;
        if (value instanceof String text && text.trim().startsWith("[")) {
            try {
                value = JSON.parseArray(text);
            } catch (Exception e) {
                return null;
            }
        }
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Object> blocks = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> normalized.put(String.valueOf(key), itemValue));
            Object view = normalized.get("view");
            if (view instanceof String viewText && viewText.trim().startsWith("{")) {
                try {
                    normalized.put("view", JSON.parseObject(viewText));
                } catch (Exception ignore) {
                    // 保留原值，由下游校验给出明确错误
                }
            }
            blocks.add(normalized);
        }
        return blocks.isEmpty() ? null : blocks;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> inputSchema() {
        Map<String, Object> blockProperties = new LinkedHashMap<>();
        blockProperties.put("type", Map.of(
                "type", "string",
                "enum", List.of("text", "view"),
                "description", "text=Markdown 正文；view=结构化视图"));
        blockProperties.put("text", Map.of(
                "type", "string",
                "description", "type=text 时的 Markdown 正文"));
        blockProperties.put("view", Map.of(
                "type", "object",
                "description", "type=view 时的 nway-view 对象，结构与正文 nway-view JSON 完全一致"));
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "object");
        block.put("properties", blockProperties);
        block.put("required", List.of("type"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("format", Map.of(
                "type", "string",
                "enum", List.of("docx", "pptx", "xlsx", "html"),
                "description", "导出格式：docx 报告、xlsx 表格、pptx 演示、html 网页"));
        properties.put("title", Map.of(
                "type", "string",
                "description", "报告标题，通常取用户问题或会话主题"));
        properties.put("blocks", Map.of(
                "type", "array",
                "description", "按展示顺序排列的导出内容，至少一项",
                "items", block));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("format", "blocks"));
        return schema;
    }
}
