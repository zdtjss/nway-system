package com.nway.system.message.support.channel.impl;

import com.nway.system.message.support.channel.MessageChannelSender;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.identity.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 邮件渠道：通过 Spring Mail 发送
 * <p>
 * 仅在配置 spring.mail.host 时启用，未配置则跳过该渠道
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class EmailChannelSender implements MessageChannelSender {

    private final JavaMailSender mailSender;

    @Value("${nway.message.mail.from:}")
    private String from;

    @Override
    public String getChannel() {
        return "email";
    }

    @Override
    public void send(MessageInfo message, List<User> receivers) {
        String[] emails = receivers.stream()
                .map(User::getEmail)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toArray(String[]::new);
        if (emails.length == 0) {
            log.warn("消息 {} 无有效邮箱，跳过邮件发送", message.getId());
            return;
        }
        if (StringUtils.isBlank(from)) {
            throw new NwaySystemException("邮件发送方未配置(nway.message.mail.from)");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(emails);
        mail.setSubject(StringUtils.defaultIfBlank(message.getTitle(), "系统消息"));
        mail.setText(message.getContent());
        mailSender.send(mail);
        log.info("邮件发送成功，消息 {} 共 {} 收件人", message.getId(), emails.length);
    }
}
