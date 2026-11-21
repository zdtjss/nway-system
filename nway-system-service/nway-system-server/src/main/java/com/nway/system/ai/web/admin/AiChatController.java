package com.nway.system.ai.web.admin;

import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiSchemaCompiler;
import com.nway.system.ai.web.support.AiSseSupport;
import java.util.List;
import java.util.Map;
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
import com.nway.system.ai.service.AiUserContexts;
import com.nway.system.ai.web.model.AiChatRequest;
import com.nway.system.ai.web.model.AiConfirmRequest;
import com.nway.system.ai.web.model.AiConversationVo;
import com.nway.system.ai.web.model.AiFileVo;
import com.nway.system.ai.web.model.AiMessageVo;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;

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

    private final AiChatService chatService;
    private final AiConversationService conversationService;
    private final AiChatFileService fileService;
    private final com.nway.system.ai.service.AiInterfaceService interfaceService;
    private final com.nway.system.ai.service.AiSchemaCompiler schemaCompiler;
    private final com.nway.system.ai.service.AiAuditService auditService;

    public AiChatController(AiChatService chatService, AiConversationService conversationService,
                            AiChatFileService fileService,
                            com.nway.system.ai.service.AiInterfaceService interfaceService,
                            com.nway.system.ai.service.AiSchemaCompiler schemaCompiler,
                            com.nway.system.ai.service.AiAuditService auditService) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.fileService = fileService;
        this.interfaceService = interfaceService;
        this.schemaCompiler = schemaCompiler;
        this.auditService = auditService;
    }

    @PostMapping(value = "chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AiChatRequest request) {
        try {
            AiUserContext user = AiUserContexts.current();
            AiChatService.ChatStream stream = chatService.stream(user, request);
            return subscribe(stream, false, user);
        } catch (Exception e) {
            return errorEmitter(e);
        }
    }

    @PostMapping(value = "chat/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirm(@RequestBody AiConfirmRequest request) {
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

    private SseEmitter subscribe(AiChatService.ChatStream stream, boolean confirmResume, AiUserContext user) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        try {
            AiSseSupport.send(emitter, AiSseSupport.event("conversation", Map.of(
                    "conversationId", stream.conversation().getId(),
                    "title", stream.conversation().getTitle() == null ? "" : stream.conversation().getTitle())));
            if (confirmResume) {
                AiSseSupport.send(emitter, AiSseSupport.event("confirm.accepted", Map.of()));
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean confirmPending = new AtomicBoolean(false);
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
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
        } else {
            disposableRef.set(disposable);
        }
        emitter.onCompletion(() -> dispose(disposableRef));
        emitter.onTimeout(() -> dispose(disposableRef));
        emitter.onError(error -> dispose(disposableRef));
        return emitter;
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
            Object inputObj = call.get("input");
            if (!(inputObj instanceof Map<?, ?> input)) {
                continue;
            }
            Object codeObj = input.get("interfaceCode");
            if (codeObj == null) {
                continue;
            }
            com.nway.system.ai.domain.AiInterfaceConfig config =
                    interfaceService.getPublishedByCode(user.effectiveTenantCode(), String.valueOf(codeObj));
            if (config == null) {
                continue;
            }
            call.put("interfaceName", config.getName());
            call.put("riskLevel", config.getRiskLevel());
            call.put("inputSchema", schemaCompiler.modelFields(config));
        }
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
