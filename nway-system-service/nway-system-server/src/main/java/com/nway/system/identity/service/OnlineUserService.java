package com.nway.system.identity.service;

import com.nway.system.identity.web.model.OnlineUserBo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nway.system.identity.service.UserService;
import com.nway.system.identity.support.TokenInvalidator;
import com.nway.system.common.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 在线用户服务（多设备会话支持）。
 * <p>
 * JWT 无状态认证本身无法感知在线用户，本服务通过在登录时往 Redis 写入会话信息
 * 实现"在线用户"追踪。每次登录生成唯一 sessionId，支持同一用户多设备在线：
 * <ul>
 *   <li>登录：写入 {@code online:user:{employeeId}:{sessionId}}，TTL = JWT 过期时间</li>
 *   <li>登出：删除当前 session 对应的 key</li>
 *   <li>查询：扫描 {@code online:user:*}</li>
 *   <li>强制下线（全部）：调用 {@link TokenInvalidator#invalidateUserTokens(String)} + 删除该用户所有 key</li>
 *   <li>踢出指定会话：仅删除指定 session key（不影响同用户其他会话）</li>
 * </ul>
 *
 * @author nway
 */
@Slf4j
@Service
public class OnlineUserService {

    private static final String KEY_PREFIX = "online:user:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenInvalidator tokenInvalidator;

    /**
     * 独立 ObjectMapper，注册 JavaTimeModule 以支持 LocalDateTime 序列化。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 登录时记录会话信息到 Redis（多设备支持）。
     *
     * @param employeeId        工号
     * @param sessionId         会话标识（JWT 中的 sid）
     * @param ip                登录 IP
     * @param userAgent         User-Agent
     * @param tokenExpireMillis Token 过期毫秒数，作为 Redis key 的 TTL
     */
    public void recordLogin(String employeeId, String sessionId, String ip, String userAgent, long tokenExpireMillis) {
        if (StringUtils.isBlank(employeeId) || StringUtils.isBlank(sessionId)) {
            return;
        }
        try {
            OnlineUserBo bo = new OnlineUserBo();
            bo.setEmployeeId(employeeId);
            bo.setSessionId(sessionId);
            bo.setIp(ip);
            bo.setUserAgent(userAgent);
            LocalDateTime loginTime = LocalDateTime.now();
            bo.setLoginTime(loginTime);
            bo.setExpireTime(loginTime.plusNanos(tokenExpireMillis * 1_000_000L));
            String json = objectMapper.writeValueAsString(bo);
            String key = KEY_PREFIX + employeeId + ":" + sessionId;
            redisTemplate.opsForValue().set(key, json, tokenExpireMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("记录在线用户失败 employeeId={} sessionId={}", employeeId, sessionId, e);
        }
    }

    /**
     * 登出时移除当前会话（兼容旧格式 key）。
     *
     * @param employeeId 工号
     * @param sessionId  会话标识（为空时按旧逻辑尝试删除）
     */
    public void removeLogin(String employeeId, String sessionId) {
        if (StringUtils.isBlank(employeeId)) {
            return;
        }
        try {
            if (StringUtils.isNotBlank(sessionId)) {
                redisTemplate.delete(KEY_PREFIX + employeeId + ":" + sessionId);
            } else {
                // 兼容：无 sessionId 时尝试删除旧格式 key
                redisTemplate.delete(KEY_PREFIX + employeeId);
            }
        } catch (Exception e) {
            log.warn("移除在线用户失败 employeeId={} sessionId={}", employeeId, sessionId, e);
        }
    }

    /**
     * 登出时移除会话（无 sessionId 兼容接口）。
     *
     * @param employeeId 工号
     */
    public void removeLogin(String employeeId) {
        removeLogin(employeeId, null);
    }

    /**
     * 查询在线用户列表，可选关键字（工号/姓名）过滤。
     *
     * @param keyword 关键字，可为空
     * @return 在线用户列表，按登录时间倒序
     */
    public List<OnlineUserBo> listOnline(String keyword) {
        List<OnlineUserBo> result = new ArrayList<>();
        Set<String> keys;
        try {
            keys = redisTemplate.keys(KEY_PREFIX + "*");
        } catch (Exception e) {
            log.warn("扫描在线用户 key 失败", e);
            return result;
        }
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        for (String key : keys) {
            try {
                Object obj = redisTemplate.opsForValue().get(key);
                if (obj == null) {
                    continue;
                }
                OnlineUserBo bo = objectMapper.readValue(obj.toString(), OnlineUserBo.class);
                if (bo == null || StringUtils.isBlank(bo.getEmployeeId())) {
                    continue;
                }
                // 兼容旧数据：如果 sessionId 为空，从 key 中尝试提取
                if (StringUtils.isBlank(bo.getSessionId())) {
                    String suffix = key.substring(KEY_PREFIX.length());
                    int colonIdx = suffix.indexOf(':');
                    if (colonIdx > 0) {
                        bo.setSessionId(suffix.substring(colonIdx + 1));
                    }
                }
                enrichUserInfo(bo);
                if (matchesKeyword(bo, keyword)) {
                    result.add(bo);
                }
            } catch (Exception e) {
                log.warn("解析在线用户失败 key={}", key, e);
            }
        }
        result.sort(Comparator.comparing(
                OnlineUserBo::getLoginTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * 强制下线指定用户的所有会话（通过 Token 版本号使所有 JWT 失效）。
     *
     * @param employeeId 工号
     */
    public void forceLogout(String employeeId) {
        if (StringUtils.isBlank(employeeId)) {
            return;
        }
        tokenInvalidator.invalidateUserTokens(employeeId);
        removeAllSessions(employeeId);
        log.info("强制下线（全部会话） employeeId={}", employeeId);
    }

    /**
     * 踢出指定会话（不影响同用户其他设备的登录状态）。
     * <p>
     * 注意：由于 JWT 无状态，踢出单会话仅移除 Redis 追踪记录。
     * 该 session 对应的 JWT 仍然有效直到过期。如需立即失效所有会话，请使用 {@link #forceLogout(String)}。
     *
     * @param employeeId 工号
     * @param sessionId  会话标识
     */
    public void forceLogoutSession(String employeeId, String sessionId) {
        if (StringUtils.isBlank(employeeId) || StringUtils.isBlank(sessionId)) {
            return;
        }
        removeLogin(employeeId, sessionId);
        log.info("踢出指定会话 employeeId={} sessionId={}", employeeId, sessionId);
    }

    /**
     * 删除指定用户的所有在线会话 key。
     */
    private void removeAllSessions(String employeeId) {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + employeeId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            // 兼容旧格式 key
            redisTemplate.delete(KEY_PREFIX + employeeId);
        } catch (Exception e) {
            log.warn("删除用户所有会话失败 employeeId={}", employeeId, e);
        }
    }

    private void enrichUserInfo(OnlineUserBo bo) {
        try {
            LoginUser loginUser = userService.getByEmployeeId(bo.getEmployeeId());
            if (loginUser != null) {
                bo.setRealName(loginUser.getRealName());
                bo.setTenantName(loginUser.getTenantName());
            }
        } catch (Exception e) {
            log.warn("补充用户信息失败 employeeId={}", bo.getEmployeeId(), e);
        }
    }

    private boolean matchesKeyword(OnlineUserBo bo, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        String kw = keyword.toLowerCase();
        return (bo.getEmployeeId() != null && bo.getEmployeeId().toLowerCase().contains(kw))
                || (bo.getRealName() != null && bo.getRealName().toLowerCase().contains(kw));
    }
}
