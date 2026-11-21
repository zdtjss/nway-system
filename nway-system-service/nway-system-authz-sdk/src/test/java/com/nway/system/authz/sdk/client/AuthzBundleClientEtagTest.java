package com.nway.system.authz.sdk.client;

import com.nway.system.authz.sdk.config.AuthzProperties;
import com.nway.system.authz.sdk.model.AuthzBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 策略包拉取的 ETag 协商：TTL 过期后携带 If-None-Match，
 * 平台返回 304 时复用本地版本且不重复解析。
 */
class AuthzBundleClientEtagTest {

    private static final String BODY = """
            {"code":200,"msg":"ok","data":{"version":7,"appCode":"demo",
             "resources":[{"resourceCode":"order","actions":["read"],"predicates":[]}]}}
            """;

    @Test
    @DisplayName("首次全量拉取后按 ETag 协商，304 复用本地版本")
    void revalidatesWithEtagAndReusesLocalBundleOn304() throws Exception {
        AuthzProperties properties = new AuthzProperties();
        properties.setPlatformUrl("http://localhost:1");
        properties.setAppCode("demo");
        properties.setAppToken("static-token");
        properties.setCacheTtl(Duration.ofMillis(10));
        properties.setStaleTtl(Duration.ofMinutes(5));

        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> secondRequestEtag = new AtomicReference<>();
        AuthzBundleClient.Transport transport = (url, token, etag) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return new AuthzBundleClient.Response(200, BODY, "\"7\"");
            }
            secondRequestEtag.set(etag);
            return new AuthzBundleClient.Response(304, "", "\"7\"");
        };

        AuthzBundleClient client = new AuthzBundleClient(properties, transport);
        AuthzBundle first = client.bundle("001");
        assertThat(first.version()).isEqualTo(7);
        assertThat(first.resources()).hasSize(1);

        Thread.sleep(40);
        AuthzBundle second = client.bundle("001");

        assertThat(calls.get()).as("TTL 过期后应再请求一次").isEqualTo(2);
        assertThat(secondRequestEtag.get()).as("第二次请求应携带 If-None-Match").isEqualTo("\"7\"");
        assertThat(second.version()).as("304 时沿用本地同版本").isEqualTo(7);
        assertThat(client.stats().errors()).isZero();
    }

    @Test
    @DisplayName("平台未返回 ETag 时以版本号作为协商值")
    void fallsBackToVersionWhenEtagMissing() throws Exception {
        AuthzProperties properties = new AuthzProperties();
        properties.setPlatformUrl("http://localhost:1");
        properties.setAppCode("demo");
        properties.setAppToken("static-token");
        properties.setCacheTtl(Duration.ofMillis(10));

        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> secondRequestEtag = new AtomicReference<>();
        AuthzBundleClient.Transport transport = (url, token, etag) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return new AuthzBundleClient.Response(200, BODY, null);
            }
            secondRequestEtag.set(etag);
            return new AuthzBundleClient.Response(200, BODY, null);
        };

        AuthzBundleClient client = new AuthzBundleClient(properties, transport);
        client.bundle("001");
        Thread.sleep(40);
        client.bundle("001");

        assertThat(secondRequestEtag.get()).isEqualTo("7");
    }
}
