package com.nway.system.file.support;

import com.nway.system.config.properties.FileProperties;
import com.nway.system.common.exception.NwaySystemException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地存储：原子写、区间读取、路径穿越防护与删除。
 */
class LocalFileStorageTest {

    private Path root;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createTempDirectory("nway-file-test-");
        FileProperties properties = new FileProperties();
        properties.getLocal().setRoot(root.toString());
        storage = new LocalFileStorage(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(root);
    }

    @Test
    @DisplayName("写入后可读、可按区间读取")
    void storeAndReadRange() throws IOException {
        String content = "0123456789";
        storage.store("t/2026/11/a.txt", new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                content.length(), "text/plain");

        assertThat(storage.exists("t/2026/11/a.txt")).isTrue();
        assertThat(storage.size("t/2026/11/a.txt")).isEqualTo(10);

        try (InputStream in = storage.openStream("t/2026/11/a.txt", 2, 3)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("234");
        }
        try (InputStream in = storage.openStream("t/2026/11/a.txt", 0, -1)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("区间读取不超过对象末尾")
    void rangeClampsToFileLength() throws IOException {
        storage.store("a.txt", new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), 3, "text/plain");

        try (InputStream in = storage.openStream("a.txt", 1, 100)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("bc");
        }
    }

    @Test
    @DisplayName("目录穿越被拒绝")
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> storage.exists("../outside.txt"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("非法存储路径");
    }

    @Test
    @DisplayName("删除后不可读")
    void deleteRemovesObject() throws IOException {
        storage.store("a.txt", new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), 3, "text/plain");
        storage.delete("a.txt");

        assertThat(storage.exists("a.txt")).isFalse();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }
}
