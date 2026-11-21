package com.nway.system.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 客户端真实 IP 解析（可信代理白名单）。
 * <p>
 * 仅当请求的直连来源（remoteAddr）命中可信代理列表时，才采信 {@code X-Forwarded-For} /
 * {@code X-Real-IP}；否则一律使用 {@code remoteAddr}，避免客户端伪造请求头绕过
 * IP 白名单与登录风控的 IP 统计。
 * <p>
 * 默认可信代理为回环与内网网段（nginx / k8s 常见形态），可通过
 * {@code nway.security.trusted-proxies} 覆盖（支持精确 IP、CIDR、前缀通配，逗号分隔）。
 *
 * @author nway
 */
@Component
public class ClientIpResolver {

    /** 默认可信代理：回环 + 私有网段 */
    private static final List<String> DEFAULT_TRUSTED_PROXIES = List.of(
            "127.0.0.0/8", "::1",
            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");

    private final List<String> trustedProxies;

    public ClientIpResolver(@Value("${nway.security.trusted-proxies:}") String configured) {
        if (StringUtils.isBlank(configured)) {
            this.trustedProxies = DEFAULT_TRUSTED_PROXIES;
        } else {
            this.trustedProxies = Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .toList();
        }
    }

    /**
     * 解析客户端 IP。
     *
     * @param request 当前请求
     * @return 客户端 IP；无法解析时返回 "unknown"
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            // 直连方不可信：忽略可伪造的代理头
            return StringUtils.isNotBlank(remoteAddr) ? remoteAddr : "unknown";
        }
        String forwarded = firstValid(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        String realIp = firstValid(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return StringUtils.isNotBlank(remoteAddr) ? remoteAddr : "unknown";
    }

    /**
     * 多级代理场景取第一个有效地址。
     */
    private String firstValid(String header) {
        if (StringUtils.isBlank(header)) {
            return null;
        }
        for (String part : header.split(",")) {
            String candidate = part.trim();
            if (StringUtils.isNotBlank(candidate) && !"unknown".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isTrustedProxy(String ip) {
        if (StringUtils.isBlank(ip)) {
            return false;
        }
        for (String pattern : trustedProxies) {
            if (matches(pattern, ip)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String pattern, String ip) {
        if (pattern.contains("/")) {
            return matchesCidr(pattern, ip);
        }
        if (pattern.endsWith("*")) {
            return ip.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equalsIgnoreCase(ip);
    }

    private boolean matchesCidr(String cidr, String ip) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            return false;
        }
        Long network = ipv4ToLong(parts[0]);
        Long address = ipv4ToLong(ip);
        if (network == null || address == null) {
            return false;
        }
        int bits;
        try {
            bits = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (bits < 0 || bits > 32) {
            return false;
        }
        long mask = bits == 0 ? 0L : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
        return (network & mask) == (address & mask);
    }

    private Long ipv4ToLong(String ip) {
        if (StringUtils.isBlank(ip) || ip.contains(":")) {
            return null;
        }
        String[] segments = ip.split("\\.");
        if (segments.length != 4) {
            return null;
        }
        long result = 0L;
        for (String segment : segments) {
            int value;
            try {
                value = Integer.parseInt(segment);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            result = (result << 8) | value;
        }
        return result;
    }
}