package com.nway.system.ai.web.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 大数据确定性导出请求：模型只提供查询计划（接口、参数、分页、格式），不接触数据。
 *
 * @author nway
 */
@Data
public class AiDataExportRequest {

    /** xlsx / csv / html / docx */
    private String format;

    private String title;

    private List<Query> queries;

    @Data
    public static class Query {

        /** 查询标识，用于返回每个查询的行数/完整性 */
        private String key;

        private String interfaceCode;

        private Map<String, Object> args;

        /** xlsx 的 sheet 名；未指定时取 label 或接口名 */
        private String sheetName;

        private Pagination pagination;

        /** 预留：声明式依赖（当前版本不支持） */
        private String dependsOn;

        /** 预留：依赖结果的逐项调用（当前版本不支持） */
        private Map<String, Object> forEach;
    }

    @Data
    public static class Pagination {

        /** PAGE：按页循环；NONE：单次取回。未指定时按 inputSchema 自动识别。 */
        private String mode;

        private String pageParam;

        private String sizeParam;

        private Integer startPage;

        private Integer pageSize;

        private Integer maxPages;
    }
}
