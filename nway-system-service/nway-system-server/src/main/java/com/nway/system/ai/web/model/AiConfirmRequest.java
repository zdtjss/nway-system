package com.nway.system.ai.web.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 写操作确认结果提交。
 *
 * @author nway
 */
@Data
public class AiConfirmRequest {

    private String conversationId;

    private String replyId;

    /** 用户选择的模型配置 ID（可选）；保持与发起本轮对话时一致 */
    private String modelId;

    private List<Decision> decisions;

    @Data
    public static class Decision {

        private String toolCallId;

        private String name;

        private boolean confirmed;

        /** 用户在确认表单中修改后的参数 */
        private Map<String, Object> args;
    }
}
