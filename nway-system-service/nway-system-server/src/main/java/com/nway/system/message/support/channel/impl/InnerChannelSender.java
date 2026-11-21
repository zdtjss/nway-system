package com.nway.system.message.support.channel.impl;

import com.nway.system.message.support.channel.MessageChannelSender;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.identity.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 站内信：消息已落库到 message_receiver，前端列表展示，无需额外发送动作
 */
@Slf4j
@Component
public class InnerChannelSender implements MessageChannelSender {

    @Override
    public String getChannel() {
        return "inner";
    }

    @Override
    public void send(MessageInfo message, List<User> receivers) {
        log.debug("站内信已落库，消息 {} 共 {} 接收人", message.getId(), receivers.size());
    }
}
