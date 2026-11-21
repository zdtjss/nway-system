package com.nway.system.message.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nway.system.common.web.PageParam;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class MessageQuery extends PageParam {
    private String title;
    private String senderId;
    private String senderName;
    private String content;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTimeBegin;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTimeEnd;
    private Boolean isRead;
    private List<String> channels;
}