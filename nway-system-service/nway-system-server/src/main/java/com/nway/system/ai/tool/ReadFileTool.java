package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.service.AiChatFileService;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.domain.AiUserContext;
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
 * 可选工具：读取对话附件文本。
 * <p>
 * 只接受当前用户上传且归属校验通过的 fileId；图片等无文本文件返回元数据提示。
 *
 * @author nway
 */
@Slf4j
public class ReadFileTool extends ToolBase {

    public static final String NAME = "read_file";

    private static final int DEFAULT_MAX_CHARS = 8_000;
    private static final int MAX_CHARS = 20_000;

    private final AiChatFileService fileService;

    public ReadFileTool(AiChatFileService fileService) {
        super(ToolBase.builder()
                .name(NAME)
                .description("""
                        按需解析并读取对话附件的文本内容。
                        仅当用户要求基于文件内容回答、或需要填写依赖文件内容的字段时调用；
                        附件仅作为业务附件（如 FILE 字段）提交时无需调用，直接使用 fileId。
                        图片已随用户消息直接提供视觉内容，不要传图片 fileId 调用本工具。
                        参数 fileId 必须是本轮或历史消息中出现的附件 id，不能编造。
                        """)
                .inputSchema(inputSchema())
                .readOnly(true)
                .concurrencySafe(true));
        this.fileService = fileService;
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
        String fileId = string(input.get("fileId"));
        if (StringUtils.isBlank(fileId)) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error=fileId 为空",
                    NAME, sessionId, toolCallId);
            return error(AiErrors.INVALID_ARGUMENT, "fileId 不能为空");
        }
        int maxChars = Math.max(200, Math.min(toInt(input.get("maxChars"), DEFAULT_MAX_CHARS), MAX_CHARS));
        try {
            AiChatFileService.FileText text = fileService.readText(fileId, user, maxChars);
            if (text.text() == null) {
                log.info("工具调用完成 tool={} session={} toolCallId={} fileId={} name={} chars=0 hasText=false "
                                + "elapsed={}ms", NAME, sessionId, toolCallId, fileId, text.name(),
                        System.currentTimeMillis() - start);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("fileId", fileId);
                body.put("name", text.name());
                body.put("text", null);
                body.put("message", "该附件无可提取文本（图片或二进制文件）。"
                        + "图片的视觉内容只在随消息发送时直接提供，请直接基于图片回答，不要重复调用 read_file；"
                        + "若看不到图片，请提示用户压缩到 5MB 以内重新上传。其他类型请提示用户改用预览或下载查看");
                return ToolResultBlock.text(JSON.toJSONString(body));
            }
            log.info("工具调用完成 tool={} session={} toolCallId={} fileId={} name={} chars={} truncated={} "
                            + "elapsed={}ms", NAME, sessionId, toolCallId, fileId, text.name(),
                    text.text().length(), text.truncated(), System.currentTimeMillis() - start);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fileId", fileId);
            body.put("name", text.name());
            body.put("text", text.text());
            body.put("truncated", text.truncated());
            return ToolResultBlock.text(JSON.toJSONString(body));
        } catch (Exception e) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} fileId={} employeeId={} error={}",
                    NAME, sessionId, toolCallId, fileId, user.getEmployeeId(), e.getMessage());
            return error(AiErrors.INVALID_ARGUMENT, e.getMessage());
        }
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
        properties.put("fileId", Map.of(
                "type", "string",
                "description", "附件 fileId（来自用户消息中的附件）"));
        properties.put("maxChars", Map.of(
                "type", "integer",
                "description", "最多返回字符数，默认 8000，最大 20000"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("fileId"));
        return schema;
    }
}
