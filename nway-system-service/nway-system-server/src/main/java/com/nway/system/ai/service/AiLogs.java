package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import io.agentscope.core.tool.ToolCallParam;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模块日志辅助：入参脱敏、文本截断与工具调用标识，避免日志泄漏密钥或写入超长内容。
 *
 * @author nway
 */
public final class AiLogs {

    private static final int MAX_SUMMARY_LENGTH = 2000;
    private static final int MAX_VALUE_LENGTH = 200;

    private AiLogs() {
    }

    /**
     * 汇总接口/工具入参：敏感字段打码、超长字符串截断，整体长度受限。
     */
    public static String summarizeArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        args.forEach((k, v) -> {
            String lower = k.toLowerCase();
            if (lower.contains("token") || lower.contains("password") || lower.contains("secret")
                    || lower.contains("apikey") || lower.contains("api_key")) {
                safe.put(k, "***");
            } else if (v instanceof String s && s.length() > MAX_VALUE_LENGTH) {
                safe.put(k, s.substring(0, MAX_VALUE_LENGTH) + "...");
            } else {
                safe.put(k, v);
            }
        });
        return truncate(JSON.toJSONString(safe), MAX_SUMMARY_LENGTH);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 会话标识：RuntimeContext.sessionId 即对话 ID，用于把工具日志与对话日志串起来。
     */
    public static String sessionId(ToolCallParam param) {
        if (param == null || param.getRuntimeContext() == null) {
            return "-";
        }
        return StringUtils.defaultIfBlank(param.getRuntimeContext().getSessionId(), "-");
    }

    public static String toolCallId(ToolCallParam param) {
        if (param == null || param.getToolUseBlock() == null) {
            return "-";
        }
        return StringUtils.defaultIfBlank(param.getToolUseBlock().getId(), "-");
    }
}
