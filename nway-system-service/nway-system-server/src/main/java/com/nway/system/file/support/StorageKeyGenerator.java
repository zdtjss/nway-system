package com.nway.system.file.support;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 服务端生成存储 key：{@code {tenantCode}/{yyyy}/{MM}/{uuid}.{ext}}。
 * <p>
 * 不使用原始文件名拼路径，避免目录穿越与特殊字符问题。
 *
 * @author nway
 */
@Component
public class StorageKeyGenerator {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    public String generate(String tenantCode, String ext) {
        String tenant = sanitizeTenant(tenantCode);
        LocalDate today = LocalDate.now();
        return tenant + "/" + today.format(YEAR) + "/" + today.format(MONTH)
                + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + ext;
    }

    private String sanitizeTenant(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return "default";
        }
        String cleaned = tenantCode.replaceAll("[^a-zA-Z0-9_-]", "_");
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
    }
}
