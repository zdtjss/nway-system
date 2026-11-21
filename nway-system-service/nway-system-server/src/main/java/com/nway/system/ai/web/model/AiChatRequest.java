package com.nway.system.ai.web.model;

import com.nway.system.ai.domain.AiPageContext;
import lombok.Data;

import java.util.List;

/**
 * 聊天请求。
 *
 * @author nway
 */
@Data
public class AiChatRequest {

    private String conversationId;

    private String message;

    private List<String> fileIds;

    /** 当前页面上下文（可选）：由前端在发送时提取的页面文本快照 */
    private AiPageContext pageContext;
}
