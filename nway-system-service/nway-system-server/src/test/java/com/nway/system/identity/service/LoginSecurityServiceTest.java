package com.nway.system.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 登录安全服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LoginSecurityServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private LoginSecurityService loginSecurityService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("记录登录失败 - 首次失败设置TTL")
    void recordFailureFirstTime() {
        when(valueOperations.increment("login:fail:emp001")).thenReturn(1L);

        int count = loginSecurityService.recordFailure("emp001");

        assertEquals(1, count);
        verify(redisTemplate).expire("login:fail:emp001", 30L, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("记录登录失败 - 非首次不重置TTL")
    void recordFailureNotFirstTime() {
        when(valueOperations.increment("login:fail:emp001")).thenReturn(3L);

        int count = loginSecurityService.recordFailure("emp001");

        assertEquals(3, count);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("获取失败次数 - 有记录")
    void getFailureCountWithRecord() {
        when(valueOperations.get("login:fail:emp001")).thenReturn(3);

        int count = loginSecurityService.getFailureCount("emp001");

        assertEquals(3, count);
    }

    @Test
    @DisplayName("获取失败次数 - 无记录返回0")
    void getFailureCountNoRecord() {
        when(valueOperations.get("login:fail:emp001")).thenReturn(null);

        int count = loginSecurityService.getFailureCount("emp001");

        assertEquals(0, count);
    }

    @Test
    @DisplayName("清除失败记录")
    void clearFailure() {
        loginSecurityService.clearFailure("emp001");

        verify(redisTemplate).delete("login:fail:emp001");
    }

    @Test
    @DisplayName("账号锁定判断 - 存在临时锁定标记")
    void isLockedWhenReachMaxAttempts() {
        when(redisTemplate.hasKey("login:lock:emp001")).thenReturn(true);

        assertTrue(loginSecurityService.isLocked("emp001"));
    }

    @Test
    @DisplayName("账号锁定判断 - 无临时锁定标记")
    void isNotLockedWhenBelowMaxAttempts() {
        when(redisTemplate.hasKey("login:lock:emp001")).thenReturn(false);

        assertFalse(loginSecurityService.isLocked("emp001"));
    }

    @Test
    @DisplayName("生成验证码 - 返回uuid和img")
    void generateCaptcha() {
        Map<String, String> result = loginSecurityService.generateCaptcha();

        assertNotNull(result);
        assertTrue(result.containsKey("uuid"));
        assertTrue(result.containsKey("img"));
        assertTrue(result.get("img").startsWith("data:image/png;base64,"));
        verify(valueOperations).set(startsWith("captcha:"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("校验验证码 - 正确")
    void validateCaptchaCorrect() {
        when(valueOperations.get("captcha:test-uuid")).thenReturn("ABCD");

        boolean result = loginSecurityService.validateCaptcha("test-uuid", "abcd");

        assertTrue(result, "验证码应忽略大小写");
        verify(redisTemplate).delete("captcha:test-uuid");
    }

    @Test
    @DisplayName("校验验证码 - 错误")
    void validateCaptchaWrong() {
        when(valueOperations.get("captcha:test-uuid")).thenReturn("ABCD");

        boolean result = loginSecurityService.validateCaptcha("test-uuid", "WXYZ");

        assertFalse(result);
        verify(redisTemplate).delete("captcha:test-uuid");
    }

    @Test
    @DisplayName("校验验证码 - 已过期")
    void validateCaptchaExpired() {
        when(valueOperations.get("captcha:test-uuid")).thenReturn(null);

        boolean result = loginSecurityService.validateCaptcha("test-uuid", "ABCD");

        assertFalse(result);
    }

    @Test
    @DisplayName("校验验证码 - 参数为null")
    void validateCaptchaNullParams() {
        assertFalse(loginSecurityService.validateCaptcha(null, "ABCD"));
        assertFalse(loginSecurityService.validateCaptcha("test-uuid", null));
    }
}
