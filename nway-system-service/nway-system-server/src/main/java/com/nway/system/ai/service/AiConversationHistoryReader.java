package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.web.model.AiFileVo;
import com.nway.system.ai.web.model.AiMessageVo;
import com.nway.system.common.tenant.TenantUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话历史读取：以 ai_message 为准，并在读取时恢复附件预览地址与导出文件下载票据。
 * <p>
 * 单独抽成服务，避免导出工具反过来依赖 {@link AiChatService} 造成循环依赖。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiConversationHistoryReader {

    /** 会产出可下载文件的工具：历史读取与导出链接刷新共用 */
    public static final Set<String> EXPORT_TOOLS = Set.of(
            "export_analysis", "export_query_data", "export_conversation", "export_tool_result");

    private static final String PAGE_CONTEXT_MARKER = "【当前页面上下文】";
    private static final String FILE_MANIFEST_MARKER = "【对话附件】";
    private static final String IMAGE_MARKER = "【图片附件】";
    private static final String ATTACHMENT_BLOCK = "attachments";
    private static final Pattern MANIFEST_FILE_ID = Pattern.compile("fileId=([^\\s；;）)]+)");

    private final AiConversationService conversationService;
    private final AiMessageService messageService;
    private final AiChatFileService fileService;
    private final AiExportService exportService;

    public AiConversationHistoryReader(AiConversationService conversationService,
                                       AiMessageService messageService,
                                       AiChatFileService fileService,
                                       AiExportService exportService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.fileService = fileService;
        this.exportService = exportService;
    }

    /**
     * 读取已落库消息并恢复附件/下载链接；会话归属校验失败时抛出异常。
     */
    public List<AiMessageVo> list(AiUserContext user, String conversationId) {
        conversationService.ensure(conversationId, user, null);
        List<AiMessageVo> messages = messageService.list(conversationId);
        for (AiMessageVo message : messages) {
            restore(user, message);
        }
        return messages;
    }

    public void restore(AiUserContext user, AiMessageVo message) {
        restoreAttachments(message, user);
        restoreExportLinks(message, user);
    }

    /**
     * 导出工具的结果里带短时下载票据，历史消息读取时按 fileId 重新签发，避免旧链接过期。
     */
    private void restoreExportLinks(AiMessageVo message, AiUserContext user) {
        List<Map<String, Object>> blocks = message.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        String tenant = user == null ? null : user.effectiveTenantCode();
        Runnable action = () -> refreshExportBlocks(message, blocks);
        if (StringUtils.isBlank(tenant)) {
            action.run();
        } else {
            TenantUtil.runAsTenant(tenant, action);
        }
    }

    private void refreshExportBlocks(AiMessageVo message, List<Map<String, Object>> blocks) {
        for (Map<String, Object> block : blocks) {
            if (!"tool_use".equals(block.get("type"))
                    || !EXPORT_TOOLS.contains(String.valueOf(block.get("name")))) {
                continue;
            }
            Object raw = block.get("result");
            if (!(raw instanceof String result) || StringUtils.isBlank(result)) {
                continue;
            }
            try {
                JSONObject json = JSON.parseObject(result);
                if (json == null || StringUtils.isBlank(json.getString("fileId"))) {
                    continue;
                }
                String url = exportService.downloadUrl(json.getString("fileId"));
                if (url == null) {
                    json.remove("url");
                    json.put("error", "FILE_EXPIRED");
                    json.put("message", "导出文件已过期或已被清理，请重新导出");
                } else {
                    json.put("url", url);
                }
                block.put("result", json.toJSONString());
            } catch (Exception e) {
                log.debug("导出文件下载地址刷新失败 messageId={} error={}", message.getId(), e.getMessage());
            }
        }
    }

    /**
     * 把历史消息中的附件清单还原为附件块，并重新生成预览地址。
     */
    private void restoreAttachments(AiMessageVo message, AiUserContext user) {
        List<Map<String, Object>> blocks = message.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        List<Map<String, Object>> files = null;
        for (Map<String, Object> block : blocks) {
            if (!"text".equals(block.get("type"))) {
                continue;
            }
            String text = String.valueOf(block.get("text"));
            if (text.startsWith(FILE_MANIFEST_MARKER)) {
                files = attachmentFiles(text, user);
                if (!files.isEmpty()) {
                    break;
                }
            }
        }
        if (files == null || files.isEmpty()) {
            return;
        }
        List<Map<String, Object>> restored = new ArrayList<>(blocks.size());
        for (Map<String, Object> block : blocks) {
            if ("text".equals(block.get("type"))) {
                String text = String.valueOf(block.get("text"));
                if (text.startsWith(FILE_MANIFEST_MARKER) || text.startsWith(IMAGE_MARKER)
                        || text.startsWith(PAGE_CONTEXT_MARKER)) {
                    continue;
                }
            }
            restored.add(block);
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", ATTACHMENT_BLOCK);
        node.put("files", files);
        restored.add(node);
        message.setBlocks(restored);
    }

    private List<Map<String, Object>> attachmentFiles(String manifest, AiUserContext user) {
        Set<String> fileIds = new LinkedHashSet<>();
        Matcher matcher = MANIFEST_FILE_ID.matcher(manifest);
        while (matcher.find()) {
            fileIds.add(matcher.group(1));
        }
        List<Map<String, Object>> files = new ArrayList<>(fileIds.size());
        for (String fileId : fileIds) {
            AiFileVo file = fileService.describe(fileId, user);
            if (file == null) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("fileId", file.getFileId());
            node.put("name", file.getName());
            node.put("size", file.getSize());
            node.put("mime", file.getMime());
            node.put("kind", file.getKind());
            node.put("previewUrl", file.getPreviewUrl());
            files.add(node);
        }
        return files;
    }
}
