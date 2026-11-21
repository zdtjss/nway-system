package com.nway.system.common.web.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis Lua 脚本的滑动窗口限流器。
 * <p>
 * 支持每秒 + 每日两级限流，原子操作保证精确计数。
 * <p>
 * 注意：脚本执行必须使用 {@link StringRedisTemplate}（纯文本序列化）。站内
 * {@code RedisTemplate} 的 value 序列化器是 {@code JacksonJsonRedisSerializer}，
 * 会把 KEYS/ARGV 写成带引号的 JSON 串（如 {@code "100"}），Lua 侧 {@code tonumber}
 * 得到 nil 导致脚本报错，fail-close 策略下所有请求都会被误判为限流（HTTP 429）。
 *
 * @author nway
 */
@Slf4j
@Component
public class RateLimiter {

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local clearBefore = now - window * 1000
            redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                return 0
            end
            redis.call('ZADD', key, now, now .. ':' .. math.random())
            redis.call('EXPIRE', key, window + 1)
            return 1
            """;

    private final DefaultRedisScript<Long> script;

    /** 脚本参数必须走纯文本序列化，不能用站内的 JSON RedisTemplate（见类注释） */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public RateLimiter() {
        script = new DefaultRedisScript<>();
        script.setScriptText(LUA_SCRIPT);
        script.setResultType(Long.class);
    }

    /**
     * 尝试获取许可（滑动窗口）。
     *
     * @param key       限流 key
     * @param windowSec 窗口大小（秒）
     * @param limit     窗口内最大请求数
     * @return true 允许，false 拒绝
     */
    public boolean tryAcquire(String key, int windowSec, int limit) {
        try {
            long now = System.currentTimeMillis();
            Long result = stringRedisTemplate.execute(script, Collections.singletonList(key),
                    String.valueOf(windowSec), String.valueOf(limit), String.valueOf(now));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("限流检查异常, key={}", key, e);
            // Redis 故障时拒绝请求（fail-close 策略），保护后端服务
            return false;
        }
    }

    /**
     * 两级限流检查（每秒 + 每日）。
     *
     * @param appCode    应用编码
     * @param rateLimit  每秒上限
     * @param dailyLimit 每日上限
     * @return true 允许，false 拒绝
     */
    public boolean checkRateLimit(String appCode, Integer rateLimit, Integer dailyLimit) {
        if (rateLimit != null && rateLimit > 0) {
            if (!tryAcquire("rate:sec:" + appCode, 1, rateLimit)) {
                log.warn("应用 [{}] 触发秒级限流", appCode);
                return false;
            }
        }
        if (dailyLimit != null && dailyLimit > 0) {
            if (!tryAcquire("rate:day:" + appCode, 86400, dailyLimit)) {
                log.warn("应用 [{}] 触发日级限流", appCode);
                return false;
            }
        }
        return true;
    }
}
