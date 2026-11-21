package com.nway.system.file.support;

import com.nway.system.config.properties.FileProperties;
import com.nway.system.common.exception.NwaySystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 存储注册表：按类型路由、默认引擎与未知类型报错。
 */
class FileStorageRegistryTest {

    @Test
    @DisplayName("按类型取引擎，未知类型抛出明确业务错误")
    void routesByType() {
        FileProperties properties = new FileProperties();
        properties.setStorageType("local");
        FileProperties fileProperties = new FileProperties();
        fileProperties.getLocal().setRoot(System.getProperty("java.io.tmpdir"));
        FileStorageRegistry registry = new FileStorageRegistry(
                List.of(new LocalFileStorage(fileProperties), new StubStorage("s3")), properties);

        assertThat(registry.defaultStorage().type()).isEqualTo("local");
        assertThat(registry.get("s3").type()).isEqualTo("s3");
        assertThat(registry.supports("local")).isTrue();
        assertThat(registry.supports("unknown")).isFalse();
        assertThatThrownBy(() -> registry.get("unknown"))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("不支持的存储类型");
    }

    @Test
    @DisplayName("默认引擎指向配置值时按类型取用")
    void defaultStorageFollowsConfig() {
        FileProperties properties = new FileProperties();
        properties.setStorageType("s3");
        FileProperties fileProperties = new FileProperties();
        fileProperties.getLocal().setRoot(System.getProperty("java.io.tmpdir"));
        FileStorageRegistry registry = new FileStorageRegistry(
                List.of(new LocalFileStorage(fileProperties), new StubStorage("s3")), properties);

        assertThat(registry.defaultStorage().type()).isEqualTo("s3");
    }

    private static class StubStorage implements FileStorage {

        private final String type;

        private StubStorage(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public void store(String key, java.io.InputStream in, long size, String contentType) {
        }

        @Override
        public long size(String key) {
            return 0;
        }

        @Override
        public java.io.InputStream openStream(String key, long offset, long length) {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public boolean exists(String key) {
            return false;
        }

        @Override
        public void delete(String key) {
        }
    }
}
