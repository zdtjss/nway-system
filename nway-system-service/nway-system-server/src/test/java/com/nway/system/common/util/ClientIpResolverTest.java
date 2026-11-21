package com.nway.system.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 客户端 IP 解析测试：仅当直连方为可信代理时才采信代理头。
 */
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver("");

    private HttpServletRequest request(String remoteAddr, String xff, String realIp) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(request.getHeader("X-Real-IP")).thenReturn(realIp);
        return request;
    }

    @Test
    @DisplayName("直连方不可信时忽略 X-Forwarded-For / X-Real-IP，返回真实直连地址")
    void untrustedRemoteShouldIgnoreForwardHeaders() {
        HttpServletRequest req = request("203.0.113.9", "1.2.3.4", "5.6.7.8");
        assertEquals("203.0.113.9", resolver.resolve(req));
    }

    @Test
    @DisplayName("直连方为回环（可信代理）时取 X-Forwarded-For 第一段")
    void trustedLoopbackShouldUseForwardedFor() {
        HttpServletRequest req = request("127.0.0.1", "1.2.3.4, 10.0.0.1", null);
        assertEquals("1.2.3.4", resolver.resolve(req));
    }

    @Test
    @DisplayName("可信代理且 XFF 缺失时回退 X-Real-IP")
    void trustedProxyShouldFallbackToRealIp() {
        HttpServletRequest req = request("192.168.1.10", null, "1.2.3.4");
        assertEquals("1.2.3.4", resolver.resolve(req));
    }

    @Test
    @DisplayName("可信代理且代理头均为 unknown 时回退直连地址")
    void trustedProxyWithUnknownHeadersShouldUseRemoteAddr() {
        HttpServletRequest req = request("10.1.2.3", "unknown", "unknown");
        assertEquals("10.1.2.3", resolver.resolve(req));
    }

    @Test
    @DisplayName("内网网段（CIDR）识别为可信代理")
    void privateRangesAreTrusted() {
        assertEquals("1.2.3.4", resolver.resolve(request("10.255.0.1", "1.2.3.4", null)));
        assertEquals("1.2.3.4", resolver.resolve(request("172.20.0.1", "1.2.3.4", null)));
        assertEquals("1.2.3.4", resolver.resolve(request("192.168.56.105", "1.2.3.4", null)));
        // 172.32.x 不在 172.16.0.0/12 内
        assertEquals("172.32.0.1", resolver.resolve(request("172.32.0.1", "1.2.3.4", null)));
    }

    @Test
    @DisplayName("XFF 首段为 unknown 时跳过取后续有效地址")
    void forwardedForShouldSkipUnknownSegment() {
        HttpServletRequest req = request("127.0.0.1", "unknown, 1.2.3.4", null);
        assertEquals("1.2.3.4", resolver.resolve(req));
    }

    @Test
    @DisplayName("自定义可信代理支持精确 IP 与前缀通配")
    void customTrustedProxies() {
        ClientIpResolver custom = new ClientIpResolver("203.0.113.0/24, proxy.internal, 10.9.*");
        assertEquals("1.2.3.4", custom.resolve(request("203.0.113.5", "1.2.3.4", null)));
        assertEquals("1.2.3.4", custom.resolve(request("10.9.1.1", "1.2.3.4", null)));
        // 未配置的地址不可信
        assertEquals("8.8.8.8", custom.resolve(request("8.8.8.8", "1.2.3.4", null)));
    }

    @Test
    @DisplayName("请求为 null 时返回 unknown")
    void nullRequestShouldReturnUnknown() {
        assertEquals("unknown", resolver.resolve(null));
    }
}