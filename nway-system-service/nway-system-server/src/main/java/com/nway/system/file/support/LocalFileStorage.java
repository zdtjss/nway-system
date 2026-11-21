package com.nway.system.file.support;

import com.nway.system.file.domain.BoundedInputStream;
import com.nway.system.config.properties.FileProperties;
import com.nway.system.common.exception.NwaySystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储引擎（v1 默认实现）。
 * <p>
 * 写入使用同目录临时文件 + 原子移动，避免并发下载读到半个文件；
 * 读取按字节区间打开，支持视频 Range 请求。
 *
 * @author nway
 */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    public static final String TYPE = "local";

    private final Path root;

    public LocalFileStorage(FileProperties properties) {
        this.root = Paths.get(properties.getLocal().getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件存储根目录：" + root, e);
        }
        log.info("本地文件存储根目录：{}", root);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void store(String key, InputStream in, long size, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
        try {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            moveAtomically(tmp, target);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    @Override
    public long size(String key) throws IOException {
        return Files.size(resolve(key));
    }

    @Override
    public InputStream openStream(String key, long offset, long length) throws IOException {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new IOException("文件不存在：" + key);
        }
        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        try {
            long total = raf.length();
            long pos = Math.min(Math.max(0, offset), total);
            raf.seek(pos);
            long limit = length < 0 ? total - pos : Math.min(length, total - pos);
            // 关闭包装流即关闭 FileChannel，进而关闭 RandomAccessFile
            return new BoundedInputStream(Channels.newInputStream(raf.getChannel()), limit);
        } catch (RuntimeException | IOException e) {
            raf.close();
            throw e;
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 归一化并校验路径仍在根目录下，即使 key 被污染也无法跳出根目录。
     */
    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new NwaySystemException("非法存储路径");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new NwaySystemException("非法存储路径");
        }
        return path;
    }
}
