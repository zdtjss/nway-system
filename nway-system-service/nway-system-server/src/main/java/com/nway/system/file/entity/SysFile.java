package com.nway.system.file.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件元数据（sys_file）。
 *
 * @author nway
 */
@Data
@Table("sys_file")
@EqualsAndHashCode(callSuper = false)
public class SysFile extends BaseEntity {

    /** 原始文件名，仅用于展示 */
    private String fileName;

    /** 存储引擎：local/s3（按记录路由读取） */
    private String storageType;

    /** 服务端生成的相对路径 */
    private String storageKey;

    /** 字节数 */
    private Long fileSize;

    /** 服务端判定的安全 MIME */
    private String contentType;

    /** 小写扩展名 */
    private String fileExt;

    /** 文件摘要（SHA-256） */
    private String sha256;

    /** 业务类型，如 factoryVisitApply */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    private String remark;
}
