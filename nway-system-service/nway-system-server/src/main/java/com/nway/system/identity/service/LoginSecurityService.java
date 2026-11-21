package com.nway.system.identity.service;

import com.nway.system.common.exception.NwaySystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 登录安全服务。
 * <p>
 * 提供登录失败追踪（临时锁定保护）、IP 级限流与验证码功能，防止暴力破解。
 * <ul>
 *   <li>登录失败追踪：Redis key {@code login:fail:{employeeId}}，TTL 30 分钟</li>
 *   <li>临时锁定：Redis key {@code login:lock:{employeeId}}，TTL 30 分钟，过期自动解锁</li>
 *   <li>IP 限流：Redis key {@code login:ip:{ip}}，TTL 60 秒，阈值 10 次</li>
 *   <li>验证码：Redis key {@code captcha:{uuid}}，TTL 5 分钟，一次性使用</li>
 * </ul>
 *
 * @author nway
 */
@Slf4j
@Service
public class LoginSecurityService {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";
    private static final String IP_KEY_PREFIX = "login:ip:";
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final long FAIL_TTL_MINUTES = 30L;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final long IP_WINDOW_SECONDS = 60L;
    private static final int IP_MAX_ATTEMPTS = 10;
    private static final long CAPTCHA_TTL_MINUTES = 5L;
    private static final int MAX_ATTEMPTS = 5;

    /** 验证码图片宽度 */
    private static final int CAPTCHA_WIDTH = 120;
    /** 验证码图片高度 */
    private static final int CAPTCHA_HEIGHT = 40;
    /** 验证码字符集（去除易混淆字符） */
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    /** 验证码长度 */
    private static final int CAPTCHA_LENGTH = 4;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== 登录失败追踪 ====================

    /**
     * 记录一次登录失败，返回当前累计失败次数。
     *
     * @param employeeId 工号
     * @return 当前失败次数
     */
    public int recordFailure(String employeeId) {
        String key = FAIL_KEY_PREFIX + employeeId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 首次失败，设置 TTL
                redisTemplate.expire(key, FAIL_TTL_MINUTES, TimeUnit.MINUTES);
            }
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.warn("记录登录失败次数失败 employeeId={}", employeeId, e);
            return 0;
        }
    }

    /**
     * 获取当前失败次数。
     *
     * @param employeeId 工号
     * @return 失败次数，无记录返回 0
     */
    public int getFailureCount(String employeeId) {
        String key = FAIL_KEY_PREFIX + employeeId;
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val == null) {
                return 0;
            }
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            log.warn("获取登录失败次数失败 employeeId={}", employeeId, e);
            return 0;
        }
    }

    /**
     * 清除失败记录（登录成功时调用）。
     *
     * @param employeeId 工号
     */
    public void clearFailure(String employeeId) {
        String key = FAIL_KEY_PREFIX + employeeId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("清除登录失败记录失败 employeeId={}", employeeId, e);
        }
    }

    /**
     * 判断账号是否已临时锁定。
     * <p>
     * 检查 Redis 中是否存在临时锁定标记（自动过期，无需管理员干预）。
     *
     * @param employeeId 工号
     * @return true 表示已锁定
     */
    public boolean isLocked(String employeeId) {
        String key = LOCK_KEY_PREFIX + employeeId;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("检查账号临时锁定状态失败 employeeId={}", employeeId, e);
            return false;
        }
    }

    /**
     * 临时锁定账号（自动过期，不需要管理员干预）。
     * <p>
     * 锁定后清除失败计数，锁定解除后重新开始计数。
     *
     * @param employeeId 工号
     */
    public void tempLock(String employeeId) {
        String key = LOCK_KEY_PREFIX + employeeId;
        try {
            redisTemplate.opsForValue().set(key, "1", LOCK_DURATION);
            clearFailure(employeeId);
            log.warn("账号 {} 连续登录失败 {} 次，已临时锁定 {} 分钟", employeeId, MAX_ATTEMPTS, LOCK_DURATION.toMinutes());
        } catch (Exception e) {
            log.warn("临时锁定账号失败 employeeId={}", employeeId, e);
        }
    }

    /**
     * 返回最大允许失败次数。
     *
     * @return 最大尝试次数
     */
    public int getMaxAttempts() {
        return MAX_ATTEMPTS;
    }

    // ==================== IP 级限流 ====================

    /**
     * 判断 IP 是否已被限流（60 秒内超过 10 次登录尝试）。
     *
     * @param ip 客户端 IP
     * @return true 表示已被限流
     */
    public boolean isIpBlocked(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String key = IP_KEY_PREFIX + ip;
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val == null) {
                return false;
            }
            return Integer.parseInt(val.toString()) >= IP_MAX_ATTEMPTS;
        } catch (Exception e) {
            log.warn("检查 IP 限流状态失败 ip={}", ip, e);
            return false;
        }
    }

    /**
     * 记录一次 IP 登录尝试。
     *
     * @param ip 客户端 IP
     */
    public void recordIpAttempt(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        String key = IP_KEY_PREFIX + ip;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, IP_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("记录 IP 登录尝试失败 ip={}", ip, e);
        }
    }

    // ==================== 验证码 ====================

    /**
     * 生成验证码并存储到 Redis。
     * <p>
     * 生成 4 位字母数字验证码，通过 Graphics2D 绘制为图片，Base64 编码返回。
     *
     * @return Map 包含 uuid（验证码标识）和 img（Base64 编码图片）
     */
    public Map<String, String> generateCaptcha() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = randomCaptchaCode();
        // 存储到 Redis
        try {
            redisTemplate.opsForValue().set(
                    CAPTCHA_KEY_PREFIX + uuid, code, CAPTCHA_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("保存验证码到 Redis 失败 uuid={}", uuid, e);
            throw new NwaySystemException("验证码生成失败，请稍后重试");
        }
        // 生成图片
        String imgBase64 = drawCaptchaImage(code);
        Map<String, String> result = new HashMap<>(2);
        result.put("uuid", uuid);
        result.put("img", imgBase64);
        return result;
    }

    /**
     * 校验验证码，校验后立即删除（一次性使用）。
     *
     * @param uuid 验证码标识
     * @param code 用户输入的验证码
     * @return true 校验通过
     */
    public boolean validateCaptcha(String uuid, String code) {
        if (uuid == null || code == null) {
            return false;
        }
        String key = CAPTCHA_KEY_PREFIX + uuid;
        try {
            Object stored = redisTemplate.opsForValue().get(key);
            // 一次性使用，无论对错都删除
            redisTemplate.delete(key);
            return stored != null && code.equalsIgnoreCase(stored.toString());
        } catch (Exception e) {
            log.warn("校验验证码失败 uuid={}", uuid, e);
            return false;
        }
    }

    // ==================== 验证码工具方法 ====================

    /**
     * 生成随机验证码。
     */
    private String randomCaptchaCode() {
        java.util.Random random = new java.util.Random();
        StringBuilder sb = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            sb.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 使用 Graphics2D 绘制验证码图片，返回 Base64 编码字符串。
     */
    private String drawCaptchaImage(String code) {
        BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            java.util.Random random = new java.util.Random();
            // 背景
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);
            // 干扰线
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
                int x1 = random.nextInt(CAPTCHA_WIDTH);
                int y1 = random.nextInt(CAPTCHA_HEIGHT);
                int x2 = random.nextInt(CAPTCHA_WIDTH);
                int y2 = random.nextInt(CAPTCHA_HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }
            // 噪点
            for (int i = 0; i < 30; i++) {
                g.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
                int x = random.nextInt(CAPTCHA_WIDTH);
                int y = random.nextInt(CAPTCHA_HEIGHT);
                g.fillRect(x, y, 1, 1);
            }
            // 验证码字符
            g.setFont(new Font("Arial", Font.BOLD | Font.ITALIC, 24));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(20 + random.nextInt(110), 20 + random.nextInt(110), 20 + random.nextInt(110)));
                int x = 8 + i * 26;
                int y = 28 + random.nextInt(6);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
            }
            // 边框
            g.setColor(Color.GRAY);
            g.drawRect(0, 0, CAPTCHA_WIDTH - 1, CAPTCHA_HEIGHT - 1);
        } finally {
            g.dispose();
        }
        // 转 Base64
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("生成验证码图片失败", e);
            throw new NwaySystemException("验证码图片生成失败");
        }
    }
}
