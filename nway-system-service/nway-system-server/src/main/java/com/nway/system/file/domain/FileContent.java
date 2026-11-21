package com.nway.system.file.domain;

import com.nway.system.file.support.FileStorage;
import com.nway.system.file.entity.SysFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 可读取的文件内容：元数据 + 按记录路由到的存储引擎。
 *
 * @author nway
 */
public final class FileContent {

    private final SysFile meta;

    private final FileStorage storage;

    public FileContent(SysFile meta, FileStorage storage) {
        this.meta = meta;
        this.storage = storage;
    }

    public String id() {
        return meta.getId();
    }

    public String fileName() {
        return meta.getFileName();
    }

    public long size() {
        return meta.getFileSize() == null ? 0 : meta.getFileSize();
    }

    public String contentType() {
        return meta.getContentType();
    }

    public InputStream openStream(long offset, long length) throws IOException {
        return storage.openStream(meta.getStorageKey(), offset, length);
    }
}
