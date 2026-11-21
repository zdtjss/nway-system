package com.nway.system.message.support.channel.impl;

import com.nway.system.message.support.channel.MessageChannelSender;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.identity.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 企业微信渠道：通过企业微信「应用消息推送」接口发送
 * 文档：https://developer.work.weixin.qq.com/document/path/90236
 */
@Slf4j
@Component
public class WxworkChannelSender implements MessageChannelSender {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://qyapi.weixin.qq.com")
            .build();

    @Value("${nway.message.wxwork.corpid:}")
    private String corpId;

    @Value("${nway.message.wxwork.corpsecret:}")
    private String corpSecret;

    @Value("${nway.message.wxwork.agentid:0}")
    private int agentId;

    private volatile String accessToken;
    private volatile long tokenExpireAt;

    @Override
    public String getChannel() {
        return "wxwork";
    }

    @Override
    public void send(MessageInfo message, List<User> receivers) {
        if (StringUtils.isAnyBlank(corpId, corpSecret)) {
            log.warn("企业微信未配置 corpid/corpsecret，跳过发送，消息 {}", message.getId());
            return;
        }
        String toUser = receivers.stream()
                .map(User::getEmployeeId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.joining("|"));
        if (toUser.isEmpty()) {
            log.warn("消息 {} 无有效企业微信接收人", message.getId());
            return;
        }
        String token = getAccessToken();
        Map<String, Object> body = Map.of(
                "touser", toUser,
                "msgtype", "text",
                "agentid", agentId,
                "text", Map.of("content", buildContent(message))
        );
        try {
            restClient.post()
                    .uri(uri -> uri.path("/cgi-bin/message/send")
                            .queryParam("access_token", token)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            log.info("企业微信发送成功，消息 {} 接收人 {}", message.getId(), toUser);
        } catch (Exception e) {
            throw new NwaySystemException("企业微信发送失败: " + e.getMessage());
        }
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return accessToken;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.get()
                    .uri(uri -> uri.path("/cgi-bin/gettoken")
                            .queryParam("corpid", corpId)
                            .queryParam("corpsecret", corpSecret)
                            .build())
                    .retrieve()
                    .body(Map.class);
            Integer errcode = (Integer) resp.get("errcode");
            if (errcode == null || errcode != 0) {
                throw new NwaySystemException("获取企业微信 access_token 失败: " + resp.get("errmsg"));
            }
            accessToken = (String) resp.get("access_token");
            Integer expiresIn = (Integer) resp.get("expires_in");
            // 提前 200 秒过期，避免边界失效
            tokenExpireAt = System.currentTimeMillis() + (expiresIn - 200) * 1000L;
            return accessToken;
        } catch (NwaySystemException e) {
            throw e;
        } catch (Exception e) {
            throw new NwaySystemException("获取企业微信 access_token 异常: " + e.getMessage());
        }
    }

    private String buildContent(MessageInfo message) {
        String title = StringUtils.defaultIfBlank(message.getTitle(), "");
        return title + "\n" + message.getContent();
    }
}
