package com.nway.system.file.support;

import com.nway.system.config.properties.FileProperties;
import com.nway.system.common.exception.NwaySystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件类型策略：扩展名白名单、大小限制、MIME 映射与文件名清洗。
 */
class FileTypePolicyTest {

    private FileTypePolicy policy() {
        FileProperties properties = new FileProperties();
        properties.setMaxSize(1024);
        return new FileTypePolicy(properties);
    }

    @Test
    @DisplayName("白名单扩展名：返回服务端判定的 MIME 与小写扩展名")
    void validatesAllowedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "照片.JPG", "application/octet-stream", new byte[]{1, 2, 3});

        FileTypePolicy.Validated validated = policy().validate(file);

        assertThat(validated.fileName()).isEqualTo("照片.JPG");
        assertThat(validated.ext()).isEqualTo("jpg");
        assertThat(validated.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("不在白名单的扩展名被拒绝")
    void rejectsUnknownExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.exe", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> policy().validate(file))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    @DisplayName("超过业务上限被拒绝")
    void rejectsOversize() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new byte[2048]);

        assertThatThrownBy(() -> policy().validate(file))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("超过限制");
    }

    @Test
    @DisplayName("空文件被拒绝")
    void rejectsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> policy().validate(file))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("文件名清洗：去除路径与控制字符")
    void sanitizesFileName() {
        assertThat(policy().sanitizeName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(policy().sanitizeName("C:\\tmp\\报告\u0000.pdf")).isEqualTo("报告.pdf");
    }

    @Test
    @DisplayName("未知扩展名的 MIME 回退为二进制流")
    void unknownExtFallsBackToOctetStream() {
        assertThat(policy().contentTypeOf("bin")).isEqualTo("application/octet-stream");
    }
}
