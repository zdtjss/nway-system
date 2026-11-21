package com.nway.system.ai.service;

import com.nway.system.ai.web.model.AiConversationVo;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.ai.dao.AiConversationDao;
import com.nway.system.ai.dao.AiMessageDao;
import com.nway.system.ai.entity.AiConversation;
import com.nway.system.ai.entity.AiModel;
import com.nway.spring.jdbc.pagination.Page;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 会话索引服务：标题、用户、最后消息时间；消息正文由 AgentScope Session 承载。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiConversationService {

    private static final String AGENT_ID = "nway-assistant";

    /** 标题最大长度，与模型提示词约束保持一致 */
    private static final int TITLE_MAX_LENGTH = 30;

    /** 生成标题时截取首条消息的最大长度，避免把大段内容全部送给模型 */
    private static final int TITLE_SOURCE_MAX_LENGTH = 1000;

    private final AiConversationDao conversationDao;
    private final AiMessageDao messageDao;
    private final AiModelService modelService;

    public AiConversationService(AiConversationDao conversationDao, AiMessageDao messageDao,
                                 AiModelService modelService) {
        this.conversationDao = conversationDao;
        this.messageDao = messageDao;
        this.modelService = modelService;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiConversation create(AiUserContext user, String firstMessage) {
        AiConversation entity = new AiConversation();
        entity.setId("c_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setUserId(user.getEmployeeId());
        entity.setAgentId(AGENT_ID);
        entity.setTitle(titleOf(firstMessage));
        entity.setLastMessageAt(LocalDateTime.now());
        entity.setStatus("ACTIVE");
        conversationDao.insert(entity);
        return entity;
    }

    /**
     * 流式对话前确保会话存在且属于当前用户。
     */
    public AiConversation ensure(String conversationId, AiUserContext user, String firstMessage) {
        if (StringUtils.isBlank(conversationId)) {
            return create(user, firstMessage);
        }
        AiConversation entity = conversationDao.queryById(conversationId);
        if (entity == null || !Strings.CS.equals(entity.getUserId(), user.getEmployeeId())) {
            throw new NwaySystemException("会话不存在或无权访问");
        }
        return entity;
    }

    public PageVo<AiConversationVo> list(AiUserContext user, int current, int size, String keyword) {
        Page<AiConversation> page = conversationDao.queryPageByUser(user.getEmployeeId(), keyword,
                Math.max(1, current), Math.max(1, Math.min(size, 100)));
        List<AiConversationVo> data = page.getPageData().stream().map(this::toVo).toList();
        return new PageVo<>(data, page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public void rename(String id, String title, AiUserContext user) {
        AiConversation entity = requireOwned(id, user);
        if (StringUtils.isBlank(title)) {
            throw new NwaySystemException("标题不能为空");
        }
        entity.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        conversationDao.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(String id, AiUserContext user) {
        AiConversation entity = requireOwned(id, user);
        entity.setStatus("ARCHIVED");
        conversationDao.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, AiUserContext user) {
        requireOwned(id, user);
        messageDao.deleteByConversation(id);
        conversationDao.deleteById(id);
    }

    /**
     * 更新最后消息时间；可能在 reactor 线程调用，显式恢复租户上下文。
     */
    public void touch(String id, AiUserContext user) {
        if (StringUtils.isBlank(id) || user == null) {
            return;
        }
        String tenant = user.getTenantCode();
        Runnable action = () -> {
            AiConversation entity = conversationDao.queryById(id);
            if (entity != null) {
                entity.setLastMessageAt(LocalDateTime.now());
                conversationDao.updateById(entity);
            }
        };
        if (StringUtils.isBlank(tenant)) {
            action.run();
        } else {
            TenantUtil.runAsTenant(tenant, action);
        }
    }

    /**
     * 新会话首条消息发送后，异步调用默认模型生成简短主题作为历史记录标题。
     * <p>
     * 只在标题仍是首条消息自动截取的原始值时回写，避免覆盖用户手动重命名；
     * 生成过程在异步线程执行，需显式恢复租户上下文。
     */
    @Async
    public void generateTitle(String conversationId, AiUserContext user, String firstMessage) {
        String source = StringUtils.trimToEmpty(firstMessage);
        if (StringUtils.isBlank(conversationId) || source.isEmpty()) {
            return;
        }
        Runnable action = () -> {
            AiConversation entity = conversationDao.queryById(conversationId);
            if (entity == null || !Strings.CS.equals(entity.getTitle(), titleOf(source))) {
                return;
            }
            String title = requestTopicTitle(conversationId, user, source);
            if (StringUtils.isBlank(title)) {
                return;
            }
            // 生成期间用户可能已重命名，写入前再确认一次
            AiConversation latest = conversationDao.queryById(conversationId);
            if (latest == null || !Strings.CS.equals(latest.getTitle(), entity.getTitle())) {
                return;
            }
            latest.setTitle(title);
            conversationDao.updateById(latest);
        };
        // 会话按创建时的租户隔离（TenantCodeStrategy 取登录租户，不随管理员工作租户切换）
        String tenant = user == null ? null : user.getTenantCode();
        if (StringUtils.isBlank(tenant)) {
            action.run();
        } else {
            TenantUtil.runAsTenant(tenant, action);
        }
    }

    /**
     * 请求模型生成主题标题；失败时返回 null，保留首条消息截取的兜底标题。
     */
    private String requestTopicTitle(String conversationId, AiUserContext user, String firstMessage) {
        long start = System.currentTimeMillis();
        try {
            AiModel modelEntity = modelService.getDefaultModel(
                    user == null ? null : user.effectiveTenantCode());
            Model model = modelService.buildModel(modelEntity);
            log.debug("生成会话标题调用大模型 conversationId={} model={} inputChars={}", conversationId,
                    modelEntity.getModelName(), firstMessage.length());
            Msg system = Msg.builder().role(MsgRole.SYSTEM).textContent("""
                    你是会话标题生成助手。根据用户的第一条消息，提炼一个简短的对话主题标题：
                    1. 只输出标题本身，不要引号、序号、解释，结尾不加标点；
                    2. 使用中文，不超过 16 个字；
                    3. 概括用户真正想办理的事情，例如「查询本月考勤」「填写报销单」。
                    """).build();
            Msg userMsg = Msg.builder().role(MsgRole.USER)
                    .textContent(StringUtils.abbreviate(firstMessage, TITLE_SOURCE_MAX_LENGTH))
                    .build();
            StringBuilder sb = new StringBuilder();
            model.stream(List.of(system, userMsg), List.of(), null)
                    .doOnNext(response -> {
                        if (response.getContent() != null) {
                            response.getContent().stream()
                                    .filter(TextBlock.class::isInstance)
                                    .map(TextBlock.class::cast)
                                    .forEach(t -> sb.append(t.getText()));
                        }
                    })
                    .blockLast(Duration.ofSeconds(30));
            String title = cleanTitle(sb.toString());
            log.info("生成会话标题完成 conversationId={} titleChars={} elapsed={}ms", conversationId,
                    StringUtils.length(title), System.currentTimeMillis() - start);
            return title;
        } catch (Exception e) {
            log.warn("生成会话标题失败 conversationId={} elapsed={}ms error={}", conversationId,
                    System.currentTimeMillis() - start, e.getMessage());
            return null;
        }
    }

    /**
     * 清理模型输出：只取首行，去掉引号/括号/# 等装饰与结尾标点，并限制长度。
     */
    private String cleanTitle(String raw) {
        String text = StringUtils.trimToEmpty(raw).lines().findFirst().orElse("").trim();
        text = text.replaceAll("^[\"'“”‘’《》\\[\\]【】#*\\s]+", "")
                .replaceAll("[\"'“”‘’《》\\[\\]【】。，,；;：:!?！？\\s]+$", "")
                .trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > TITLE_MAX_LENGTH ? text.substring(0, TITLE_MAX_LENGTH) : text;
    }

    private AiConversation requireOwned(String id, AiUserContext user) {
        AiConversation entity = conversationDao.queryById(id);
        if (entity == null || !Strings.CS.equals(entity.getUserId(), user.getEmployeeId())) {
            throw new NwaySystemException("会话不存在或无权访问");
        }
        return entity;
    }

    private AiConversationVo toVo(AiConversation entity) {
        AiConversationVo vo = new AiConversationVo();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setStatus(entity.getStatus());
        vo.setLastMessageAt(entity.getLastMessageAt());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private String titleOf(String message) {
        String text = StringUtils.trimToEmpty(message);
        if (text.isEmpty()) {
            return "新对话";
        }
        return text.length() > 30 ? text.substring(0, 30) : text;
    }
}
