package com.nway.system.ai.domain;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 入参字段定义（对应 ai-interface.schema.json inputField）。
 *
 * @author nway
 */
@Data
public class AiFieldDef {

    private String key;

    private String label;

    /** STRING/TEXT/INTEGER/NUMBER/BOOLEAN/DATE/DATETIME/ENUM/MULTI_ENUM/ARRAY/OBJECT/FILE/FILE_ARRAY */
    private String type;

    private Boolean required;

    @com.fasterxml.jackson.annotation.JsonProperty("default")
    @JSONField(name = "default")
    private Object defaultValue;

    private String description;

    /** PATH/QUERY/BODY/HEADER，默认 BODY */
    private String location;

    /** LLM/CONSTANT/USER_CONTEXT/TOOL_CONTEXT */
    private String valueSource;

    /** 注入表达式，如 ${user.employeeId} */
    private String valueExpression;

    private Map<String, Object> validation;

    private List<AiOption> options;

    private AiOptionSource optionSource;

    private Boolean sensitive;

    /** 嵌套 body 路径，如 form.visitCustomer */
    private String jsonPath;

    private AiFieldItems items;

    private List<AiFieldDef> properties;

    /** 模型可见：valueSource 为空或 LLM */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @JSONField(serialize = false)
    public boolean isLlmVisible() {
        return valueSource == null || valueSource.isBlank() || "LLM".equalsIgnoreCase(valueSource);
    }

    @Data
    public static class AiOption {
        private String label;
        private Object value;
        private Object parentValue;
        private Boolean disabled;
    }

    @Data
    public static class AiOptionSource {
        /** DICT/HTTP/SEARCH */
        private String type;
        private String dictCode;
        private String appCode;
        private String path;
        private String method;
        private String valueField;
        private String labelField;
        private String parentField;
        private String keywordParam;
        private Integer pageSize;
        private Integer cacheTtlSeconds;
    }

    @Data
    public static class AiFieldItems {
        private String type;
        private String description;
        private List<AiFieldDef> properties;
    }
}
