package com.nway.system.authz.sdk.config;

import com.nway.system.authz.sdk.client.AuthzBundleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 启动自检：验证 SDK 能换取 Token 并访问策略包接口。
 * <p>
 * 遵循降级原则 P1：自检失败只告警，不阻断应用启动（平台短暂不可用时业务仍可用，
 * 由 stale-while-error 与 fail-open 配置决定实际行为）。
 */
public class AuthzStartupChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthzStartupChecker.class);

    private final AuthzBundleClient client;
    private final boolean enabled;

    public AuthzStartupChecker(AuthzBundleClient client, boolean enabled) {
        this.client = client;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("授权 SDK 启动自检已关闭（nway.authz.startup-check=false）");
            return;
        }
        try {
            client.probe();
            log.info("授权 SDK 启动自检通过：平台可达且应用凭据有效");
        } catch (RuntimeException e) {
            log.warn("授权 SDK 启动自检未通过（不阻断启动）：{}", e.getMessage());
        }
    }
}
