package com.nway.system.file.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据展示 VO。
 *
 * @author nway
 */
@Data
public class FileVo {

    private String id;

    private String fileName;

    private Long fileSize;

    private String contentType;

    private String fileExt;

    private String bizType;

    private String bizId;

    private String remark;

    private String creatorId;

    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** 下载地址（相对 context-path） */
    private String downloadUrl;

    /** 内联预览地址（相对 context-path） */
    private String previewUrl;
}
