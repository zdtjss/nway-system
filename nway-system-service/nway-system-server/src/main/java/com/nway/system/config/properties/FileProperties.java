package com.nway.system.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件中心配置（{@code nway.file.*}）。
 *
 * @author nway
 */
@Data
@Component
@ConfigurationProperties(prefix = "nway.file")
public class FileProperties {

    /** 新上传使用的存储引擎，与 FileStorage.type() 对应 */
    private String storageType = "local";

    /** 业务校验上限（字节），必须不大于 multipart 上限 */
    private long maxSize = 200L * 1024 * 1024;

    /** 允许上传的扩展名，逗号分隔（不带点，大小写不敏感） */
    private String allowedExtensions = "jpg,jpeg,png,gif,webp,bmp,"
            + "mp4,webm,ogg,"
            + "pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,zip,rar,7z";

    /** 预览/下载票据有效期（秒） */
    private long ticketTtlSeconds = 600;

    private Local local = new Local();

    private S3 s3 = new S3();

    @Data
    public static class Local {

        /** 本地磁盘根目录，必须位于 Web 静态目录之外 */
        private String root = "./data/files";
    }

    /**
     * S3/MinIO 扩展占位配置。v1 不实现，仅保留扩展点。
     */
    @Data
    public static class S3 {

        private boolean enabled = false;

        private String endpoint;

        private String bucket;

        private String accessKey;

        private String secretKey;
    }
}
