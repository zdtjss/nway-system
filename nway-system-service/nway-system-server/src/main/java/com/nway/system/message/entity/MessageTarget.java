package com.nway.system.message.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

@Data
@Table("message_target")
public class MessageTarget {

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;
    private String messageId;
    private Integer targetType;
    private String targetId;
}