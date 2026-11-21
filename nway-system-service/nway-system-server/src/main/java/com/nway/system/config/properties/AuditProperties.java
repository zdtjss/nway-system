package com.nway.system.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 审计日志配置属性。
 *
 * @author nway
 */
@Data
@Component
@ConfigurationProperties(prefix = "nway.audit")
public class AuditProperties {

    /** 排除审计记录的路径（如高频轮询接口） */
    private Set<String> excludePaths = Set.of("/message/polling", "/login/refresh");
}
