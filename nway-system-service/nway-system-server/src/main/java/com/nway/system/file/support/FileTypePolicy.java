package com.nway.system.file.support;

import com.nway.system.config.properties.FileProperties;
import com.nway.system.common.exception.NwaySystemException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文件类型策略：文件名清洗、扩展名白名单、大小限制与服务端安全 MIME 映射。
 * <p>
 * 不信任客户端 {@code Content-Type}：展示/下载用的 MIME 一律按扩展名映射，
 * 未知类型回退为 {@code application/octet-stream}。
 *
 * @author nway
 */
@Component
public class FileTypePolicy {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");

    private static final int MAX_NAME_LENGTH = 255;

    private static final Map<String, String> EXT_MIME = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("ogg", "video/ogg"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"));

    private final FileProperties properties;

    public FileTypePolicy(FileProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验上传文件并返回服务端认可的元数据。
     */
    public Validated validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NwaySystemException("上传文件不能为空");
        }
        String fileName = sanitizeName(file.getOriginalFilename());
        if (fileName.length() > MAX_NAME_LENGTH) {
            throw new NwaySystemException("文件名长度不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        long maxSize = properties.getMaxSize();
        if (file.getSize() > maxSize) {
            throw new NwaySystemException("文件大小超过限制（最大 " + (maxSize / 1024 / 1024) + "MB）");
        }
        String ext = extensionOf(fileName);
        if (StringUtils.isBlank(ext)) {
            throw new NwaySystemException("文件名缺少扩展名");
        }
        if (!allowedExtensions().contains(ext)) {
            throw new NwaySystemException("不支持的文件类型：" + ext);
        }
        return new Validated(fileName, ext, contentTypeOf(ext), file.getSize());
    }

    /**
     * 按扩展名映射服务端安全 MIME；未知类型回退为二进制流。
     */
    public String contentTypeOf(String ext) {
        return EXT_MIME.getOrDefault(StringUtils.lowerCase(ext), "application/octet-stream");
    }

    /**
     * 清洗原始文件名：去除路径与控制字符，仅保留展示用名称。
     */
    public String sanitizeName(String originalFilename) {
        if (StringUtils.isBlank(originalFilename)) {
            throw new NwaySystemException("文件名不能为空");
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = CONTROL_CHARS.matcher(name).replaceAll("").trim();
        if (StringUtils.isBlank(name) || ".".equals(name) || "..".equals(name)) {
            throw new NwaySystemException("文件名不合法");
        }
        return name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return StringUtils.lowerCase(fileName.substring(dot + 1));
    }

    private Set<String> allowedExtensions() {
        Set<String> set = new LinkedHashSet<>();
        for (String item : StringUtils.split(StringUtils.defaultString(properties.getAllowedExtensions()), ',')) {
            String ext = StringUtils.lowerCase(StringUtils.trimToEmpty(item));
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (StringUtils.isNotBlank(ext)) {
                set.add(ext);
            }
        }
        return set;
    }

    /**
     * 校验通过后的文件元数据。
     *
     * @param fileName    清洗后的展示文件名
     * @param ext         小写扩展名
     * @param contentType 服务端判定的安全 MIME
     * @param size        字节数
     */
    public record Validated(String fileName, String ext, String contentType, long size) {
    }
}
