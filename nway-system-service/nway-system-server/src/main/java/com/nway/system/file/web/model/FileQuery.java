package com.nway.system.file.web.model;

import com.nway.system.common.web.PageParam;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 文件元数据分页查询条件。
 *
 * @author nway
 */
@Data
public class FileQuery extends PageParam {

    /** 文件名（模糊匹配） */
    private String fileName;

    /** 业务类型（精确匹配） */
    private String bizType;

    /** 上传人工号（精确匹配） */
    private String creatorId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
