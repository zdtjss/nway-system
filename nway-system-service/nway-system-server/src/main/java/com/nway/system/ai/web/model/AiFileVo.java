package com.nway.system.ai.web.model;

import lombok.Data;

/**
 * 对话附件视图。
 *
 * @author nway
 */
@Data
public class AiFileVo {

    private String fileId;

    private String name;

    private Long size;

    private String mime;

    /** IMAGE/PDF/WORD/EXCEL/PPT/TEXT/OTHER */
    private String kind;

    private String previewUrl;

    /** PENDING/READY/FAILED */
    private String extractionStatus;

    private String summary;
}
