package com.nway.system.file.support;

import java.io.IOException;
import java.io.InputStream;

/**
 * 单一存储引擎的读写契约。
 * <p>
 * 新增引擎只需实现本接口并注册为 Spring Bean，上传/下载编排与 Controller 均无需改动。
 *
 * @author nway
 */
public interface FileStorage {

    /** 引擎标识，与 {@code sys_file.storage_type} 对应，如 local/s3 */
    String type();

    /** 写入对象，key 由上层生成 */
    void store(String key, InputStream in, long size, String contentType) throws IOException;

    /** 读取对象大小 */
    long size(String key) throws IOException;

    /**
     * 按字节区间读取。
     * offset=0, length&lt;0 表示读取全部；用于支持 HTTP Range（视频拖动进度）。
     */
    InputStream openStream(String key, long offset, long length) throws IOException;

    /** 判断对象是否存在 */
    boolean exists(String key);

    /** 删除对象（物理文件） */
    void delete(String key) throws IOException;
}
