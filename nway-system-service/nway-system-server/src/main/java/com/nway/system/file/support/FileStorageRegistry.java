package com.nway.system.file.support;

import com.nway.system.config.properties.FileProperties;
import com.nway.system.common.exception.NwaySystemException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储引擎注册表。
 * <p>
 * 写操作使用配置的默认引擎，读操作使用文件记录上的 {@code storage_type}，
 * 保证切换默认存储后存量文件仍可下载。
 *
 * @author nway
 */
@Component
public class FileStorageRegistry {

    private final Map<String, FileStorage> storages;
    private final FileProperties properties;

    public FileStorageRegistry(List<FileStorage> storageList, FileProperties properties) {
        Map<String, FileStorage> map = new LinkedHashMap<>();
        for (FileStorage storage : storageList) {
            FileStorage previous = map.putIfAbsent(storage.type(), storage);
            if (previous != null) {
                throw new IllegalStateException("存储引擎类型重复：" + storage.type());
            }
        }
        this.storages = Map.copyOf(map);
        this.properties = properties;
    }

    public FileStorage get(String type) {
        FileStorage storage = storages.get(type);
        if (storage == null) {
            throw new NwaySystemException("不支持的存储类型：" + type);
        }
        return storage;
    }

    public FileStorage defaultStorage() {
        return get(properties.getStorageType());
    }

    public boolean supports(String type) {
        return storages.containsKey(type);
    }
}
