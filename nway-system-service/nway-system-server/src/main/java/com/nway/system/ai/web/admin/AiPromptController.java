package com.nway.system.ai.web.admin;

import com.nway.system.ai.service.AiChatService;
import com.nway.system.ai.service.AiPromptService;
import com.nway.system.ai.service.AiUserContexts;
import com.nway.system.ai.web.model.AiPromptSaveRequest;
import com.nway.system.ai.web.model.AiPromptVo;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.common.web.Result;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词：SYSTEM / ROLE / USER 三级，个人可覆盖、管理员可提升。
 *
 * @author nway
 */
@RequiresPermission(resource = "ai_chat", appCode = "public_app")
@RestController
@RequestMapping("ai/prompts")
public class AiPromptController {

    private final AiPromptService promptService;
    private final AiChatService chatService;

    public AiPromptController(AiPromptService promptService, AiChatService chatService) {
        this.promptService = promptService;
        this.chatService = chatService;
    }

    @GetMapping
    public Result<List<AiPromptVo>> list(@RequestParam(defaultValue = "false") boolean includeAll) {
        AiUserContext user = AiUserContexts.current();
        boolean admin = user.isAdmin() || user.isSuperAdmin();
        return Result.ok(promptService.listVisible(user, includeAll && admin));
    }

    @PostMapping
    public Result<AiPromptVo> create(@RequestBody AiPromptSaveRequest request) {
        return Result.ok(promptService.savePersonal(request, AiUserContexts.current()));
    }

    @PutMapping("{id}")
    public Result<AiPromptVo> update(@PathVariable String id, @RequestBody AiPromptSaveRequest request) {
        AiUserContext user = AiUserContexts.current();
        return Result.ok(promptService.update(id, request, user, isAdmin(user)));
    }

    @DeleteMapping("{id}")
    public Result<Void> delete(@PathVariable String id) {
        AiUserContext user = AiUserContexts.current();
        promptService.delete(id, user, isAdmin(user));
        return Result.ok();
    }

    @PostMapping("{id}/promote")
    public Result<AiPromptVo> promote(@PathVariable String id,
                                      @RequestParam String scopeType,
                                      @RequestParam(required = false) String scopeId,
                                      @RequestParam(required = false) String roleCode) {
        AiUserContext user = AiUserContexts.current();
        return Result.ok(promptService.promote(id, scopeType, scopeId, roleCode, user, isAdmin(user)));
    }

    /**
     * 从会话消息提取提示词草稿（独立模型调用，不直接保存）。
     */
    @PostMapping("extract")
    public Result<Map<String, Object>> extract(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return Result.error("conversationId 不能为空");
        }
        AiUserContext user = AiUserContexts.current();
        String conversationId = StringUtils.trimToNull(String.valueOf(body.get("conversationId")));
        if (conversationId == null) {
            return Result.error("conversationId 不能为空");
        }
        String name = body.get("name") == null ? "对话提取" : String.valueOf(body.get("name"));
        String promptType = body.get("promptType") == null ? "TASK" : String.valueOf(body.get("promptType"));
        String transcript = chatService.transcriptText(user, conversationId);
        String draft = promptService.draftFromConversation(name, promptType, transcript, user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("promptType", promptType);
        data.put("content", draft);
        return Result.ok(data);
    }

    private boolean isAdmin(AiUserContext user) {
        return user.isAdmin() || user.isSuperAdmin();
    }
}
