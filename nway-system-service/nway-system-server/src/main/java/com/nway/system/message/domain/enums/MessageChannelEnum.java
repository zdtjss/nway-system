package com.nway.system.message.domain.enums;

import com.nway.system.common.exception.NwaySystemException;
import lombok.Getter;

/**
 * 消息发送渠道
 */
@Getter
public enum MessageChannelEnum {

    INNER("inner", "站内信"),
    INNER_POP("inner_pop", "站内弹窗"),
    EMAIL("email", "邮件"),
    WXWORK("wxwork", "企业微信");

    private final String code;
    private final String desc;

    MessageChannelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MessageChannelEnum of(String code) {
        for (MessageChannelEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new NwaySystemException("无效的消息渠道: " + code);
    }
}
