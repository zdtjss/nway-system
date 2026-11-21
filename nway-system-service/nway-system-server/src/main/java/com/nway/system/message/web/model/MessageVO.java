package com.nway.system.message.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class MessageVO {
    private String id;
    private String receiverId;
    private String title;
    private String senderId;
    private String senderName;
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private Boolean isRead;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;
    private List<String> channels;
    private Map<String, Integer> channelStatus;
}