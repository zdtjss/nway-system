package com.nway.system.ai.web.admin;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiSchemaCompiler;
import com.nway.system.ai.web.support.AiSseSupport;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nway.system.ai.service.AiChatFileService;
import com.nway.system.ai.service.AiChatService;
import com.nway.system.ai.service.AiConversationService;
import com.nway.system.ai.service.AiModelService;
import com.nway.system.ai.service.AiUserContexts;
import com.nway.system.ai.web.model.AiChatModelVo;
import com.nway.system.ai.web.model.AiChatRequest;
import com.nway.system.ai.web.model.AiConfirmRequest;
import com.nway.system.ai.web.model.AiConversationVo;
import com.nway.system.ai.web.model.AiFileVo;
import com.nway.system.ai.web.model.AiMessageVo;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;

import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * AI 对话：SSE 流式、写操作确认、会话历史、对话附件。
 *
 * @author nway
 */
@Slf4j
@RequiresPermission(resource = "ai_chat", appCode = "public_app")
@RestController
@RequestMapping("ai")
public class AiChatController {

    private static final long SSE_TIMEOUT_MS = 600_000L;

    /**
     * 单机内的活动对话流：同一会话同一时刻只允许一个生成任务。
     * 浏览器断开（前端超时/停止/关闭页面）时后端不一定能立刻感知，遗留流会一直占着会话写入权，
     * 新消息因此排队或报 “Session already has an active writer”，所以这里显式登记并支持取消。
     */
    private final Map<String, ActiveStream> activeStreams = new ConcurrentHashMap<>();

    private record ActiveStream(String key, SseEmitter emitter, AtomicBoolean finished,
                                AtomicReference<Disposable> disposable,
                                AtomicReference<String> replyId, boolean cancelable) {
    }

    private final AiChatService chatService;
    private final AiConversationService conversationService;
    private final AiChatFileService fileService;
    private final AiModelService modelService;
    private final com.nway.system.ai.service.AiInterfaceService interfaceService;
    private final com.nway.system.ai.service.AiSchemaCompiler schemaCompiler;
    private final com.nway.system.ai.service.AiAuditService auditService;

    public AiChatController(AiChatService chatService, AiConversationService conversationService,
                            AiChatFileService fileService, AiModelService modelService,
                            com.nway.system.ai.service.AiInterfaceService interfaceService,
                            com.nway.system.ai.service.AiSchemaCompiler schemaCompiler,
                            com.nway.system.ai.service.AiAuditService auditService) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.fileService = fileService;
        this.modelService = modelService;
        this.interfaceService = interfaceService;
        this.schemaCompiler = schemaCompiler;
        this.auditService = auditService;
    }

    @PostMapping(value = "chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AiChatRequest request, HttpServletResponse response) {
        disableProxyBuffering(response);
        try {
            AiUserContext user = AiUserContexts.current();
            AiChatService.ChatStream stream = chatService.stream(user, request);
            return subscribe(stream, false, user);
        } catch (Exception e) {
            return errorEmitter(e);
        }
    }

    @PostMapping(value = "chat/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirm(@RequestBody AiConfirmRequest request, HttpServletResponse response) {
        disableProxyBuffering(response);
        try {
            AiUserContext user = AiUserContexts.current();
            if (request.getDecisions() != null) {
                request.getDecisions().forEach(decision -> {
                    Object code = decision.getArgs() == null ? null : decision.getArgs().get("interfaceCode");
                    if (code != null) {
                        auditService.recordConfirm(user, String.valueOf(code), decision.isConfirmed());
                    }
                });
            }
            AiChatService.ChatStream stream = chatService.confirm(user, request);
            return subscribe(stream, true, user);
        } catch (Exception e) {
            return errorEmitter(e);
        }
    }

    /**
     * 停止当前会话正在生成的回复。前端在连接中断、空闲超时或用户点击停止后调用，
     * 及时结束本轮事件流并释放会话写入权，避免下一条消息长时间排队或被写锁拒绝。
     */
    @PostMapping("chat/cancel")
    public Result<Void> cancel(@RequestBody(required = false) Map<String, Object> body) {
        Object rawConversationId = body == null ? null : body.get("conversationId");
        String conversationId = rawConversationId == null ? null : String.valueOf(rawConversationId).trim();
        if (conversationId == null || conversationId.isEmpty()) {
            return Result.ok();
        }
        AiUserContext user = AiUserContexts.current();
        ActiveStream active = activeStreams.get(streamKey(user, conversationId));
        if (active == null) {
            return Result.ok();
        }
        if (!active.cancelable()) {
            // 写操作确认后的执行流不允许被打断，避免业务接口调用到一半被取消
            log.info("忽略写操作执行流的取消请求 conversationId={} employeeId={}",
                    conversationId, user.getEmployeeId());
            return Result.ok();
        }
        Object rawReplyId = body.get("replyId");
        String replyId = rawReplyId == null ? null : String.valueOf(rawReplyId);
        String currentReplyId = active.replyId().get();
        // 携带 replyId 时必须是当前这一轮：停止后用户立刻发了新消息的场景里，
        // 迟到的取消请求不能把新一轮（可能还没发出 reply.start）顶掉
        if (replyId != null && !replyId.equals(currentReplyId)) {
            return Result.ok();
        }
        log.info("用户停止生成 conversationId={} employeeId={} replyId={}",
                conversationId, user.getEmployeeId(), currentReplyId);
        cancelStream(active);
        return Result.ok();
    }

    // ==================== 模型 ====================

    /** 对话页可选模型：用户只能看到并使用当前租户可用、启用中的模型。 */
    @GetMapping("chat/models")
    public Result<List<AiChatModelVo>> chatModels() {
        return Result.ok(modelService.listChatModels(AiUserContexts.current().effectiveTenantCode()));
    }

    // ==================== 会话 ====================

    @GetMapping("conversations")
    public Result<PageVo<AiConversationVo>> conversations(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(conversationService.list(AiUserContexts.current(), current, size, keyword));
    }

    @PostMapping("conversations")
    public Result<AiConversationVo> createConversation(@RequestBody(required = false) Map<String, Object> body) {
        AiUserContext user = AiUserContexts.current();
        String message = body == null ? null : String.valueOf(body.getOrDefault("message", ""));
        return Result.ok(chatService.conversationVo(conversationService.create(user, message)));
    }

    @GetMapping("conversations/{id}/messages")
    public Result<List<AiMessageVo>> messages(@PathVariable String id) {
        return Result.ok(chatService.history(AiUserContexts.current(), id));
    }

    @PatchMapping("conversations/{id}")
    public Result<Void> renameConversation(@PathVariable String id,
                                           @RequestBody(required = false) Map<String, Object> body) {
        String title = body == null ? "" : String.valueOf(body.getOrDefault("title", ""));
        conversationService.rename(id, title, AiUserContexts.current());
        return Result.ok();
    }

    @PostMapping("conversations/{id}/archive")
    public Result<Void> archiveConversation(@PathVariable String id) {
        conversationService.archive(id, AiUserContexts.current());
        return Result.ok();
    }

    @DeleteMapping("conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable String id) {
        conversationService.delete(id, AiUserContexts.current());
        return Result.ok();
    }

    // ==================== 附件 ====================

    @PostMapping("files")
    public Result<AiFileVo> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.upload(file));
    }

    @DeleteMapping("files/{id}")
    public Result<Void> deleteFile(@PathVariable String id) {
        fileService.delete(id, AiUserContexts.current());
        return Result.ok();
    }

    @PostMapping("files/{id}/extract")
    public Result<AiFileVo> extract(@PathVariable String id) {
        return Result.ok(fileService.retryExtract(id, AiUserContexts.current()));
    }

    // ==================== SSE ====================

    /** 关闭网关与浏览器缓冲，否则 SSE 分片会被攒包，首字延迟且容易整段超时。 */
    private void disableProxyBuffering(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }

    private SseEmitter subscribe(AiChatService.ChatStream stream, boolean confirmResume, AiUserContext user) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        String conversationId = stream.conversation().getId();
        String key = streamKey(user, conversationId);
        boolean cancelable = !confirmResume;
        ActiveStream active = new ActiveStream(key, emitter, finished, disposableRef,
                new AtomicReference<>(), cancelable);
        ActiveStream previous = activeStreams.put(key, active);
        if (previous != null && previous.cancelable()) {
            // 上一轮多半已被客户端放弃（超时/断网/关页）却不自知，新消息直接顶掉，避免排队等待
            log.info("终止会话遗留的生成任务 conversationId={} employeeId={}",
                    conversationId, user.getEmployeeId());
            cancelStream(previous);
        }
        try {
            AiSseSupport.send(emitter, AiSseSupport.event("conversation", Map.of(
                    "conversationId", conversationId,
                    "title", stream.conversation().getTitle() == null ? "" : stream.conversation().getTitle())));
            if (confirmResume) {
                AiSseSupport.send(emitter, AiSseSupport.event("confirm.accepted", Map.of()));
            }
        } catch (Exception e) {
            activeStreams.remove(key, active);
            emitter.completeWithError(e);
            return emitter;
        }

        AtomicBoolean confirmPending = new AtomicBoolean(false);
        Disposable disposable = stream.events().subscribe(
                event -> {
                    if (finished.get()) {
                        return;
                    }
                    try {
                        Map<String, Object> payload = AiSseSupport.toPayload(event);
                        if (payload == null) {
                            return;
                        }
                        if (payload.get("replyId") != null) {
                            active.replyId().set(String.valueOf(payload.get("replyId")));
                        }
                        if ("confirm.required".equals(payload.get("type"))) {
                            enrichConfirm(payload, user);
                            confirmPending.set(true);
                            AiSseSupport.send(emitter, payload);
                            return;
                        }
                        // 确认后 SDK 还会发 REQUEST_STOP/AGENT_END；不能让前端提前结束等待确认
                        if (confirmPending.get() && "reply.end".equals(payload.get("type"))) {
                            return;
                        }
                        if ("reply.end".equals(payload.get("type")) && stream.lengthLimited().get()) {
                            payload.put("truncated", true);
                            payload.putIfAbsent("finishReason", "length");
                        }
                        AiSseSupport.send(emitter, payload);
                    } catch (Exception e) {
                        finished.set(true);
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    if (finished.compareAndSet(false, true)) {
                        log.warn("AI 流式对话失败: {}", error.getMessage());
                        try {
                            AiSseSupport.send(emitter, AiSseSupport.event("error",
                                    Map.of("message", friendlyError(error))));
                        } catch (Exception ignore) {
                            // 客户端已断开
                        }
                        emitter.complete();
                    }
                },
                () -> {
                    if (finished.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                });
        if (finished.get()) {
            disposable.dispose();
            activeStreams.remove(key, active);
        } else {
            disposableRef.set(disposable);
        }
        emitter.onCompletion(() -> {
            activeStreams.remove(key, active);
            dispose(disposableRef);
        });
        emitter.onTimeout(() -> {
            activeStreams.remove(key, active);
            dispose(disposableRef);
        });
        emitter.onError(error -> {
            activeStreams.remove(key, active);
            dispose(disposableRef);
        });
        return emitter;
    }

    private String streamKey(AiUserContext user, String conversationId) {
        return user.effectiveTenantCode() + ":" + user.getEmployeeId() + ":" + conversationId;
    }

    /** 结束并移除一个活动流：dispose 会取消上游 Agent 事件流，从而释放会话写入权。 */
    private void cancelStream(ActiveStream active) {
        active.finished().set(true);
        activeStreams.remove(active.key(), active);
        dispose(active.disposable());
        try {
            active.emitter().complete();
        } catch (Exception ignore) {
            // 客户端已断开或响应已结束
        }
    }

    /**
     * 确认表单需要接口入参 Schema：按 toolCall 中的 interfaceCode 补充。
     */
    @SuppressWarnings("unchecked")
    private void enrichConfirm(Map<String, Object> payload, AiUserContext user) {
        Object calls = payload.get("toolCalls");
        if (!(calls instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> call = (Map<String, Object>) item;
            Map<String, Object> input = normalizeInput(call.get("input"));
            if (input == null) {
                continue;
            }
            Object codeObj = input.get("interfaceCode");
            if (codeObj == null) {
                continue;
            }
            String interfaceCode = String.valueOf(codeObj);
            AiInterfaceConfig config = interfaceService.getPublishedByCode(
                    user.effectiveTenantCode(), interfaceCode);
            if (config == null) {
                // 跨租户共享的接口可能不在当前租户的已发布列表中，按编码兜底
                config = interfaceService.getAnyPublishedByCode(interfaceCode);
            }
            if (config == null) {
                continue;
            }
            // 事件里的 input 可能是 JSON 字符串，统一回写为对象，前端才能取到 args
            call.put("input", input);
            call.put("interfaceCode", config.getInterfaceCode());
            call.put("interfaceName", config.getName());
            call.put("riskLevel", config.getRiskLevel());
            call.put("inputSchema", schemaCompiler.modelFields(config));
        }
    }

    /** tool input 可能是 Map，也可能是模型返回的 JSON 字符串。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeInput(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (raw instanceof String text && text.trim().startsWith("{")) {
            try {
                return JSON.parseObject(text);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private SseEmitter errorEmitter(Throwable error) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        try {
            AiSseSupport.send(emitter, AiSseSupport.event("error",
                    Map.of("message", friendlyError(error))));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void dispose(AtomicReference<Disposable> ref) {
        Disposable disposable = ref.get();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private String friendlyError(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "AI 服务暂时不可用，请稍后重试" : message;
    }
}
