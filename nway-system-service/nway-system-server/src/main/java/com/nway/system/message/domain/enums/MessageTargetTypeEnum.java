package com.nway.system.message.domain.enums;

import com.nway.system.common.exception.NwaySystemException;
import lombok.Getter;

/**
 * 消息目标类型，对齐数据库 message_target.target_type
 */
@Getter
public enum MessageTargetTypeEnum {

    USER(1, "用户"),
    DEPT(2, "部门"),
    ROLE(3, "角色");

    private final int code;
    private final String desc;

    MessageTargetTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MessageTargetTypeEnum of(Integer code) {
        if (code == null) {
            throw new NwaySystemException("消息目标类型不能为空");
        }
        for (MessageTargetTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new NwaySystemException("无效的消息目标类型: " + code);
    }
}
