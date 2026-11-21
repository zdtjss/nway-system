package com.nway.system.identity.service;

import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.common.exception.NwaySystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 密码策略服务。
 * <p>
 * 提供密码强度校验与密码历史校验，防止使用弱密码或重复密码。
 * <ul>
 *   <li>强度规则：最小长度 8、必须包含大小写字母和数字</li>
 *   <li>密码历史：Redis key {@code pwd:history:{employeeId}}，存最近 5 次 BCrypt 哈希</li>
 * </ul>
 *
 * @author nway
 */
@Slf4j
@Service
public class PasswordPolicyService {

    private static final String HISTORY_KEY_PREFIX = "pwd:history:";
    private static final long HISTORY_TTL_DAYS = 90L;
    private static final int MAX_HISTORY_SIZE = 5;

    private static final int MIN_LENGTH = 8;

    private static final Pattern UPPER_CASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER_CASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 校验密码是否符合策略。
     * <p>
     * 规则：
     * <ul>
     *   <li>最小长度 8</li>
     *   <li>必须包含大写字母</li>
     *   <li>必须包含小写字母</li>
     *   <li>必须包含数字</li>
     * </ul>
     * 校验失败时抛出 {@link NwaySystemException}，携带具体原因。
     *
     * @param password 待校验的原始密码
     */
    public void validatePassword(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new NwaySystemException(ErrorCodeEnum.PASSWORD_POLICY_VIOLATION,
                    "密码长度不能少于 " + MIN_LENGTH + " 位");
        }
        if (!UPPER_CASE_PATTERN.matcher(password).matches()) {
            throw new NwaySystemException(ErrorCodeEnum.PASSWORD_POLICY_VIOLATION,
                    "密码必须包含大写字母");
        }
        if (!LOWER_CASE_PATTERN.matcher(password).matches()) {
            throw new NwaySystemException(ErrorCodeEnum.PASSWORD_POLICY_VIOLATION,
                    "密码必须包含小写字母");
        }
        if (!DIGIT_PATTERN.matcher(password).matches()) {
            throw new NwaySystemException(ErrorCodeEnum.PASSWORD_POLICY_VIOLATION,
                    "密码必须包含数字");
        }
    }

    /**
     * 检查新密码是否与历史密码重复。
     * <p>
     * 遍历 Redis 中存储的最近 5 次密码 BCrypt 哈希，逐一匹配。
     *
     * @param employeeId  工号
     * @param rawPassword 原始密码
     * @return true 表示与历史重复
     */
    public boolean checkHistory(String employeeId, String rawPassword) {
        String key = HISTORY_KEY_PREFIX + employeeId;
        try {
            List<Object> history = redisTemplate.opsForList().range(key, 0, -1);
            if (history == null || history.isEmpty()) {
                return false;
            }
            for (Object stored : history) {
                if (stored != null && PASSWORD_ENCODER.matches(rawPassword, stored.toString())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("校验密码历史失败 employeeId={}", employeeId, e);
        }
        return false;
    }

    /**
     * 记录密码历史。
     * <p>
     * 将新密码的 BCrypt 哈希存入 Redis List，仅保留最近 {@value #MAX_HISTORY_SIZE} 条。
     *
     * @param employeeId       工号
     * @param encodedPassword  BCrypt 编码后的密码
     */
    public void recordPassword(String employeeId, String encodedPassword) {
        String key = HISTORY_KEY_PREFIX + employeeId;
        try {
            redisTemplate.opsForList().rightPush(key, encodedPassword);
            // 裁剪列表，仅保留最近 MAX_HISTORY_SIZE 条
            redisTemplate.opsForList().trim(key, -MAX_HISTORY_SIZE, -1);
            // 刷新 TTL
            redisTemplate.expire(key, HISTORY_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("记录密码历史失败 employeeId={}", employeeId, e);
        }
    }
}
