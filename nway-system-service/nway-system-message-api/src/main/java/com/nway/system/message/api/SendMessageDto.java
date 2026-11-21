package com.nway.system.message.api;

import lombok.Data;

import java.util.List;

@Data
public class SendMessageDto {

    private String title;
    private String content;
    private String senderId;
    private String senderName;
    private List<String> channels;
    private List<MessageTargetDTO> targets;

    @Data
    public static class MessageTargetDTO {
        private Integer targetType;
        private String targetId;
    }
}