package com.nway.system.message.support.channel;

import com.nway.system.message.entity.MessageInfo;
import com.nway.system.identity.entity.User;

import java.util.List;

/**
 * 消息渠道发送器，每种发送渠道一个实现
 */
public interface MessageChannelSender {

    /**
     * 渠道编码，对应 MessageChannelEnum.code
     */
    String getChannel();

    /**
     * 发送消息给指定接收人
     *
     * @param message   消息主体
     * @param receivers 接收人列表（含邮箱、工号等）
     */
    void send(MessageInfo message, List<User> receivers);
}
