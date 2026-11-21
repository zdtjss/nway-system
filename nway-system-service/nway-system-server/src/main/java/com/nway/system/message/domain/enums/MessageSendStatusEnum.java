package com.nway.system.message.domain.enums;

import com.nway.system.common.exception.NwaySystemException;
import lombok.Getter;

/**
 * 消息发送状态，对齐数据库 message_send_log.status
 */
@Getter
public enum MessageSendStatusEnum {

    PENDING("pending", "待发送"),
    SUCCESS("success", "发送成功"),
    FAILED("failed", "发送失败");

    private final String code;
    private final String desc;

    MessageSendStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MessageSendStatusEnum of(String code) {
        for (MessageSendStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new NwaySystemException("无效的发送状态: " + code);
    }
}
