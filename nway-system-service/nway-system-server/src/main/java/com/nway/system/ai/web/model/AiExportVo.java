package com.nway.system.ai.web.model;

import lombok.Data;

/**
 * 导出结果：文件已落文件中心，前端凭短时票据地址直接下载。
 *
 * @author nway
 */
@Data
public class AiExportVo {

    private String fileId;

    private String fileName;

    private Long size;

    /** 浏览器可直接访问的下载地址（context-path 相对，带短时票据） */
    private String url;
}
