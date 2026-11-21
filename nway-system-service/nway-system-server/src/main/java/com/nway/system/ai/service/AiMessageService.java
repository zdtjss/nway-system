package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.web.model.AiMessageVo;
import com.nway.system.ai.dao.AiMessageDao;
import com.nway.system.ai.entity.AiMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * 幂等保存：只插入库中尚不存在的消息，重复调用不会产生重复记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveIfAbsent(String conversationId, String userId, List<AiMessageVo> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Set<String> existing = new HashSet<>();
        for (AiMessage item : messageDao.queryMsgIdsByConversation(conversationId)) {
            existing.add(item.getMsgId());
        }
        for (AiMessageVo vo : messages) {
            if (StringUtils.isBlank(vo.getId()) || !existing.add(vo.getId())) {
                continue;
            }
            AiMessage entity = new AiMessage();
            // 主键由 StringIdStrategy 生成：模型响应 id（chatcmpl-*）不保证全局唯一
            entity.setMsgId(vo.getId());
            entity.setConversationId(conversationId);
            entity.setUserId(userId);
            entity.setRole(vo.getRole());
            entity.setBlocksJson(JSON.toJSONString(vo.getBlocks() == null ? List.of() : vo.getBlocks()));
            entity.setMessageTime(vo.getTimestamp());
            messageDao.insert(entity);
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
