package com.nway.system.ai.web.support;

import com.alibaba.fastjson2.JSON;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.ToolUseBlock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AgentScope 事件 → SSE 载荷映射。
 * <p>
 * 前端按 payload.type 分发：thinking.* / message.* / tool.call.* / tool.result.* /
 * confirm.required / reply.end / error。
 *
 * @author nway
 */
public final class AiSseSupport {

    private AiSseSupport() {
    }

    public static void send(SseEmitter emitter, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event().data(JSON.toJSONString(payload)));
    }

    public static Map<String, Object> toPayload(AgentEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (event.getType()) {
            case AGENT_START -> {
                payload.put("type", "reply.start");
                payload.put("replyId", ((AgentStartEvent) event).getReplyId());
            }
            case THINKING_BLOCK_START -> {
                payload.put("type", "thinking.start");
                payload.put("blockId", ((ThinkingBlockStartEvent) event).getBlockId());
            }
            case THINKING_BLOCK_DELTA -> {
                payload.put("type", "thinking.delta");
                payload.put("blockId", ((ThinkingBlockDeltaEvent) event).getBlockId());
                payload.put("delta", ((ThinkingBlockDeltaEvent) event).getDelta());
            }
            case THINKING_BLOCK_END -> {
                payload.put("type", "thinking.end");
                payload.put("blockId", ((ThinkingBlockEndEvent) event).getBlockId());
            }
            case TEXT_BLOCK_START -> {
                payload.put("type", "message.start");
                payload.put("blockId", ((TextBlockStartEvent) event).getBlockId());
            }
            case TEXT_BLOCK_DELTA -> {
                // query_interface 等 returnDirect 工具的结果已由工具卡片展示，
                // 框架会把同一份结果再合成一条助手正文，这里丢弃避免重复展示。
                if (isReturnDirect(event)) {
                    return null;
                }
                payload.put("type", "message.delta");
                payload.put("blockId", ((TextBlockDeltaEvent) event).getBlockId());
                payload.put("delta", ((TextBlockDeltaEvent) event).getDelta());
            }
            case TEXT_BLOCK_END -> {
                payload.put("type", "message.end");
                payload.put("blockId", ((TextBlockEndEvent) event).getBlockId());
            }
            case TOOL_CALL_START -> {
                ToolCallStartEvent e = (ToolCallStartEvent) event;
                payload.put("type", "tool.call.start");
                payload.put("toolCallId", e.getToolCallId());
                payload.put("name", e.getToolCallName());
            }
            case TOOL_CALL_END -> {
                ToolCallEndEvent e = (ToolCallEndEvent) event;
                payload.put("type", "tool.call.end");
                payload.put("toolCallId", e.getToolCallId());
                payload.put("name", e.getToolCallName());
            }
            case TOOL_RESULT_START -> {
                ToolResultStartEvent e = (ToolResultStartEvent) event;
                payload.put("type", "tool.result.start");
                payload.put("toolCallId", e.getToolCallId());
                payload.put("name", e.getToolCallName());
            }
            case TOOL_RESULT_TEXT_DELTA -> {
                ToolResultTextDeltaEvent e = (ToolResultTextDeltaEvent) event;
                payload.put("type", "tool.result.delta");
                payload.put("toolCallId", e.getToolCallId());
                payload.put("name", e.getToolCallName());
                payload.put("delta", e.getDelta());
            }
            case TOOL_RESULT_END -> {
                ToolResultEndEvent e = (ToolResultEndEvent) event;
                payload.put("type", "tool.result.end");
                payload.put("toolCallId", e.getToolCallId());
                payload.put("name", e.getToolCallName());
                if (e.getState() != null) {
                    payload.put("state", e.getState().getValue());
                }
            }
            case REQUIRE_USER_CONFIRM -> {
                RequireUserConfirmEvent e = (RequireUserConfirmEvent) event;
                payload.put("type", "confirm.required");
                payload.put("replyId", e.getReplyId());
                payload.put("toolCalls", toolCalls(e.getToolCalls()));
            }
            case ALL_TOOLS_DENIED -> {
                AllToolsDeniedEvent e = (AllToolsDeniedEvent) event;
                payload.put("type", "tool.denied");
                payload.put("toolCalls", toolCalls(e.getDeniedToolCalls()));
            }
            case AGENT_END -> {
                payload.put("type", "reply.end");
                payload.put("replyId", ((AgentEndEvent) event).getReplyId());
            }
            case EXCEED_MAX_ITERS -> {
                ExceedMaxItersEvent e = (ExceedMaxItersEvent) event;
                payload.put("type", "error");
                payload.put("message", "已达到最大推理轮次（" + e.getMaxIters() + "），请缩小问题范围后重试");
            }
            case REQUEST_STOP -> {
                payload.put("type", "reply.end");
                payload.put("stopped", true);
            }
            default -> {
                return null;
            }
        }
        return payload;
    }

    public static Map<String, Object> event(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        if (data != null) {
            payload.putAll(data);
        }
        return payload;
    }

    private static List<Map<String, Object>> toolCalls(List<ToolUseBlock> toolCalls) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (toolCalls == null) {
            return list;
        }
        for (ToolUseBlock toolCall : toolCalls) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", toolCall.getId());
            node.put("name", toolCall.getName());
            node.put("input", toolCall.getInput());
            list.add(node);
        }
        return list;
    }

    /** returnDirect 工具合成的最终正文事件；结果已通过工具卡片展示。 */
    private static boolean isReturnDirect(AgentEvent event) {
        Map<String, Object> metadata = event.getMetadata();
        return metadata != null && GenerateReason.TOOL_RETURN_DIRECT.name()
                .equals(metadata.get(AgentEvent.METADATA_GENERATE_REASON));
    }
}
