package com.nway.system.common.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 敏感数据脱敏工具类。
 * <p>
 * 提供手机号、身份证号、邮箱、姓名等敏感字段的脱敏处理。
 * 所有方法均为 null 安全且幂等（已脱敏的值不会再次处理）。
 * </p>
 *
 * @author nway
 */
public final class SensitiveDataUtil {

    private SensitiveDataUtil() {
    }

    /**
     * 手机号脱敏：保留前 3 位和后 4 位，中间用 * 替换。
     * <p>例：13812345678 → 138****5678</p>
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        return mask(phone, 3, 4);
    }

    /**
     * 身份证号脱敏：保留前 6 位和后 4 位，中间用 * 替换。
     * <p>例：110101199001011234 → 110101********1234</p>
     *
     * @param idCard 原始身份证号
     * @return 脱敏后的身份证号
     */
    public static String maskIdCard(String idCard) {
        return mask(idCard, 6, 4);
    }

    /**
     * 邮箱脱敏：本地名保留首字符，其余用 * 替换，域名保持不变。
     * <p>例：test@example.com → t***@example.com</p>
     *
     * @param email 原始邮箱
     * @return 脱敏后的邮箱
     */
    public static String maskEmail(String email) {
        if (StringUtils.isBlank(email) || alreadyMasked(email)) {
            return email;
        }
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) {
            // 不含 @ 或本地名为空，按通用规则脱敏
            return mask(email, 1, 0);
        }
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (local.length() <= 1) {
            return email;
        }
        return local.charAt(0) + repeat('*', local.length() - 1) + domain;
    }

    /**
     * 姓名脱敏：保留首字符，其余用 * 替换。
     * <p>例：张三丰 → 张**；张三 → 张*</p>
     *
     * @param name 原始姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        return mask(name, 1, 0);
    }

    /**
     * 通用脱敏：保留前 prefixKeep 位和后 suffixKeep 位，中间用 * 替换。
     * <p>已包含 * 的值视为已脱敏，原样返回；长度不足时仅保留首字符并脱敏其余。</p>
     *
     * @param value      原始值
     * @param prefixKeep 保留前缀长度
     * @param suffixKeep 保留后缀长度
     * @return 脱敏后的值
     */
    public static String mask(String value, int prefixKeep, int suffixKeep) {
        if (StringUtils.isBlank(value) || alreadyMasked(value)) {
            return value;
        }
        int len = value.length();
        // 长度不足以同时保留前后缀时，仅保留首字符，其余脱敏
        if (len <= prefixKeep + suffixKeep) {
            if (len <= 1) {
                return value;
            }
            return value.charAt(0) + repeat('*', len - 1);
        }
        int maskLen = len - prefixKeep - suffixKeep;
        return value.substring(0, prefixKeep) + repeat('*', maskLen) + value.substring(len - suffixKeep);
    }

    /**
     * 判断是否已脱敏（包含 * 即认为已处理，保证幂等）。
     */
    private static boolean alreadyMasked(String value) {
        return value.indexOf('*') >= 0;
    }

    private static String repeat(char ch, int count) {
        if (count <= 0) {
            return "";
        }
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, ch);
        return new String(chars);
    }
}
