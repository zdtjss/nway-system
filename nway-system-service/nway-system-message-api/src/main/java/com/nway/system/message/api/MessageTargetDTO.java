package com.nway.system.message.api;


import lombok.Data;

@Data
public class MessageTargetDTO {
    private String MessageId;
    private Integer targetType;
    private String targetId;
}