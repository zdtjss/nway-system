package com.nway.system.ai.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话索引视图。
 *
 * @author nway
 */
@Data
public class AiConversationVo {

    private String id;

    private String title;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastMessageAt;

    private LocalDateTime createTime;
}
