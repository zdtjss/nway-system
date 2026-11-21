package com.nway.system.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * 文件接口跨域配置（供外部系统前端直连调用）。
 * <p>
 * 默认不开放；配置 {@code nway.file.cors-allowed-origins} 后生效（逗号分隔，支持通配模式）。
 * 显式暴露下载相关响应头，前端才能读取文件名与分段信息。
 *
 * @author nway
 */
@Configuration
public class FileCorsConfig implements WebMvcConfigurer {

    private static final String[] EXPOSED_HEADERS = {
            "Content-Disposition", "Content-Range", "Accept-Ranges"
    };

    @Value("${nway.file.cors-allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.stream(StringUtils.split(StringUtils.defaultString(allowedOrigins), ','))
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .toList();
        if (origins.isEmpty()) {
            return;
        }
        String[] patterns = origins.toArray(new String[0]);
        for (String mapping : new String[]{"/file/**", "/open/file/**"}) {
            registry.addMapping(mapping)
                    .allowedOriginPatterns(patterns)
                    .allowedMethods("GET", "POST", "HEAD", "OPTIONS")
                    .allowedHeaders("*")
                    .exposedHeaders(EXPOSED_HEADERS)
                    .maxAge(3600);
        }
    }
}
