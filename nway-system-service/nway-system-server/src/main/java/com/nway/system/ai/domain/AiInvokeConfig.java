package com.nway.system.ai.domain;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 调用契约（对应 ai-interface.schema.json invokeConfig）。
 *
 * @author nway
 */
@Data
public class AiInvokeConfig {

    private String baseUrlOverride;

    /** PATH 参数名顺序（可选，路径占位符由字段 location=PATH 自动替换） */
    private List<String> pathParams;

    /** JSON/FORM/RAW */
    private String bodyMode;

    /** 固定 body 骨架，字段按 jsonPath 填入 */
    private Map<String, Object> bodyTemplate;

    /** 固定请求头（不允许覆盖 Authorization） */
    private Map<String, String> headers;

    /** 文件字段映射，如 {"fileId": "json-string"} */
    private Map<String, String> fileMapping;

    private Response response;

    private Integer timeoutMs;

    private Integer maxResponseBytes;

    /**
     * 可选分页元数据：导出链路识别分页参数用；未配置时按 inputSchema 自动识别。
     */
    private Pagination pagination;

    @Data
    public static class Pagination {
        /** PAGE：按页循环；NONE：单次取回。为空时自动识别。 */
        private String mode;
        private String pageParam;
        private String sizeParam;
        private Integer startPage;
        private Integer pageSize;
        private Integer maxPages;
    }

    @Data
    public static class Response {
        private String codePath;
        private List<Object> successCodes;
        private String messagePath;
        private String dataPath;
        private Map<String, String> outputMapping;
    }
}
