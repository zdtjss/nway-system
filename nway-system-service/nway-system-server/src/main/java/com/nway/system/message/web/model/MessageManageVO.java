package com.nway.system.message.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息管理列表 VO：含接收人数与已读数统计
 */
@Data
public class MessageManageVO {

    private String messageId;
    private String title;
    private String senderId;
    private String senderName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sendTime;
    private Integer receiverCount;
    private Integer readCount;
    private List<String> channels;
    private String tenantCode;
    private String tenantName;
}
