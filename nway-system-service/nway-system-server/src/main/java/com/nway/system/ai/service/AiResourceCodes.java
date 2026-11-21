package com.nway.system.ai.service;

/**
 * 接口 → 权限资源编码。
 * <p>
 * sys_resource.resource_code 为 VARCHAR(50)，接口编码最长 64；超长时追加短哈希，
 * 保证登记与运行时判定使用同一算法。
 *
 * @author nway
 */
public final class AiResourceCodes {

    private static final int MAX_LENGTH = 50;

    private AiResourceCodes() {
    }

    public static String of(String interfaceCode) {
        String raw = "ai:" + interfaceCode;
        if (raw.length() <= MAX_LENGTH) {
            return raw;
        }
        String hash = Integer.toHexString(interfaceCode.hashCode());
        int keep = MAX_LENGTH - hash.length() - 1;
        return raw.substring(0, Math.max(0, keep)) + ":" + hash;
    }
}
