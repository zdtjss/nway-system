package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.web.model.AiMessageVo;
import com.nway.system.ai.dao.AiMessageDao;
import com.nway.system.ai.entity.AiMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话消息持久化：消息正文以 ai_message 表为权威存储。
 * <p>
 * 写入按消息 id 幂等：每轮对话结束后把 AgentScope 会话中的消息回写数据库，
 * 重复回写不会产生重复记录；读取只查数据库。
 *
 * @author nway
 */
@Service
public class AiMessageService {

    private final AiMessageDao messageDao;

    public AiMessageService(AiMessageDao messageDao) {
        this.messageDao = messageDao;
    }

    public List<AiMessageVo> list(String conversationId) {
        List<AiMessageVo> result = new ArrayList<>();
        for (AiMessage entity : messageDao.queryByConversation(conversationId)) {
            result.add(toVo(entity));
        }
        return result;
    }

    /**
     * 幂等回写：库中不存在的消息插入，正文发生变化的已有消息原地更新。
     * <p>
     * 写操作确认后，工具结果在下一轮流式（/ai/chat/confirm）结束后才出现在会话里，
     * 此时 tool_use 消息已落库；只插入不更新会让历史记录中的工具块永久缺少结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(String conversationId, String userId, List<AiMessageVo> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Map<String, String> existing = new HashMap<>();
        for (AiMessage item : messageDao.queryMsgBlocksByConversation(conversationId)) {
            existing.put(item.getMsgId(), item.getBlocksJson());
        }
        for (AiMessageVo vo : messages) {
            if (StringUtils.isBlank(vo.getId())) {
                continue;
            }
            String blocksJson = JSON.toJSONString(vo.getBlocks() == null ? List.of() : vo.getBlocks());
            if (existing.containsKey(vo.getId())) {
                if (!blocksJson.equals(existing.get(vo.getId()))) {
                    messageDao.updateBlocksJson(conversationId, vo.getId(), blocksJson);
                    existing.put(vo.getId(), blocksJson);
                }
                continue;
            }
            AiMessage entity = new AiMessage();
            // 主键由 StringIdStrategy 生成：模型响应 id（chatcmpl-*）不保证全局唯一
            entity.setMsgId(vo.getId());
            entity.setConversationId(conversationId);
            entity.setUserId(userId);
            entity.setRole(vo.getRole());
            entity.setBlocksJson(blocksJson);
            entity.setMessageTime(vo.getTimestamp());
            messageDao.insert(entity);
            existing.put(vo.getId(), blocksJson);
        }
    }

    public void deleteByConversation(String conversationId) {
        messageDao.deleteByConversation(conversationId);
    }

    @SuppressWarnings("unchecked")
    private AiMessageVo toVo(AiMessage entity) {
        AiMessageVo vo = new AiMessageVo();
        vo.setId(entity.getMsgId());
        vo.setRole(entity.getRole());
        vo.setTimestamp(entity.getMessageTime());
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (StringUtils.isNotBlank(entity.getBlocksJson())) {
            for (Object item : JSON.parseArray(entity.getBlocksJson())) {
                if (item instanceof Map<?, ?> map) {
                    blocks.add((Map<String, Object>) map);
                }
            }
        }
        vo.setBlocks(blocks);
        return vo;
    }
}
