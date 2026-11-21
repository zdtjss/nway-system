package com.nway.system.openplatform.service;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * SSO 授权码服务。
 * <p>
 * 授权码存储在 Redis 中，5 分钟有效，一次性消费。
 *
 * @author nway
 */
@Slf4j
@Service
public class OpenSsoService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 16; // 16 bytes = 32 hex chars
    private static final String CODE_KEY_PREFIX = "sso:code:";
    private static final long CODE_TTL_SECONDS = 300; // 5 分钟

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 生成 SSO 授权码。
     *
     * @param appCode      应用编码
     * @param employeeId   用户工号
     * @param redirectUri  回调地址
     * @return 授权码
     */
    public String generateAuthCode(String appCode, String employeeId, String redirectUri) {
        String code = generateCode();
        AuthCodeInfo info = new AuthCodeInfo(appCode, employeeId, redirectUri);
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + code, JSON.toJSONString(info),
                CODE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("SSO 授权码生成: appCode={}, employeeId={}", appCode, employeeId);
        return code;
    }

    /**
     * 消费授权码（一次性）。
     *
     * @param code 授权码
     * @return 授权码信息；无效或已消费返回 null
     */
    public AuthCodeInfo consumeAuthCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String key = CODE_KEY_PREFIX + code;
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            return null;
        }
        // 立即删除（一次性消费）
        redisTemplate.delete(key);
        try {
            String json = raw instanceof String s ? s : JSON.toJSONString(raw);
            return JSON.parseObject(json, AuthCodeInfo.class);
        } catch (Exception e) {
            log.error("解析授权码信息失败", e);
            return null;
        }
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_LENGTH];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 授权码信息。
     */
    @Data
    public static class AuthCodeInfo {
        private String appCode;
        private String employeeId;
        private String redirectUri;

        public AuthCodeInfo() {
        }

        public AuthCodeInfo(String appCode, String employeeId, String redirectUri) {
            this.appCode = appCode;
            this.employeeId = employeeId;
            this.redirectUri = redirectUri;
        }
    }
}
