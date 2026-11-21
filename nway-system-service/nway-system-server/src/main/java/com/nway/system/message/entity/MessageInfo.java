package com.nway.system.message.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Table("message_info")
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class MessageInfo extends BaseEntity {
    private String title;
    private String content;
    private String senderId;
    private String senderName;
    private LocalDateTime sendTime;
    private LocalDateTime expireTime;
}