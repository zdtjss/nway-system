package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiInterfaceConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把接口元数据编译为模型可理解的 JSON Schema。
 * <p>
 * 规则：valueSource 非 LLM 的字段不进入 Schema，由后端注入身份/常量。
 *
 * @author nway
 */
@Component
public class AiSchemaCompiler {

    /**
     * 面向模型的字段清单：只含 LLM 可见字段，选项集过大时仅返回 optionSource 摘要。
     */
    public List<Map<String, Object>> modelFields(AiInterfaceConfig config) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (config.getInputSchema() == null) {
            return result;
        }
        for (AiFieldDef field : config.getInputSchema()) {
            if (field.isLlmVisible()) {
                result.add(modelField(field));
            }
        }
        return result;
    }

    private Map<String, Object> modelField(AiFieldDef field) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("key", field.getKey());
        node.put("label", field.getLabel());
        node.put("type", field.getType());
        node.put("required", Boolean.TRUE.equals(field.getRequired()));
        node.put("description", field.getDescription());
        if (field.getDefaultValue() != null) {
            node.put("default", field.getDefaultValue());
        }
        if (StringUtils.isNotBlank(field.getLocation())) {
            node.put("location", field.getLocation());
        }
        if (StringUtils.isNotBlank(field.getJsonPath())) {
            node.put("jsonPath", field.getJsonPath());
        }
        if (field.getValidation() != null && !field.getValidation().isEmpty()) {
            node.put("validation", field.getValidation());
        }
        if (field.getOptions() != null && !field.getOptions().isEmpty()) {
            if (field.getOptions().size() <= 50) {
                node.put("options", field.getOptions());
            } else {
                node.put("optionHint", "选项超过 50 个，请先向用户确认关键词，再由系统按关键词解析");
            }
        } else if (field.getOptionSource() != null) {
            AiFieldDef.AiOptionSource source = field.getOptionSource();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", source.getType());
            summary.put("keywordParam", source.getKeywordParam());
            summary.put("pageSize", source.getPageSize());
            if (source.getDictCode() != null) {
                summary.put("dictCode", source.getDictCode());
            }
            node.put("optionSource", summary);
        }
        if (field.getItems() != null) {
            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", field.getItems().getType());
            items.put("description", field.getItems().getDescription());
            if (field.getItems().getProperties() != null) {
                items.put("properties", field.getItems().getProperties().stream()
                        .map(this::modelField).toList());
            }
            node.put("items", items);
        }
        if (field.getProperties() != null && !field.getProperties().isEmpty()) {
            node.put("properties", field.getProperties().stream().map(this::modelField).toList());
        }
        return node;
    }

    public Map<String, Object> compile(AiInterfaceConfig config) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        List<String> required = new ArrayList<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        if (config.getInputSchema() != null) {
            for (AiFieldDef field : config.getInputSchema()) {
                if (!field.isLlmVisible()) {
                    continue;
                }
                properties.put(field.getKey(), fieldSchema(field));
                if (Boolean.TRUE.equals(field.getRequired())) {
                    required.add(field.getKey());
                }
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    public Map<String, Object> fieldSchema(AiFieldDef field) {
        Map<String, Object> node = new LinkedHashMap<>();
        String type = StringUtils.upperCase(field.getType());
        String description = describe(field);
        switch (type) {
            case "INTEGER" -> node.put("type", "integer");
            case "NUMBER" -> node.put("type", "number");
            case "BOOLEAN" -> node.put("type", "boolean");
            case "DATE" -> {
                node.put("type", "string");
                node.put("format", "date");
            }
            case "DATETIME" -> {
                node.put("type", "string");
                node.put("format", "date-time");
            }
            case "ENUM" -> {
                node.put("type", "string");
                if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                    node.put("enum", field.getOptions().stream().map(AiFieldDef.AiOption::getValue).toList());
                }
            }
            case "MULTI_ENUM" -> {
                node.put("type", "array");
                Map<String, Object> items = new LinkedHashMap<>();
                items.put("type", "string");
                if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                    items.put("enum", field.getOptions().stream().map(AiFieldDef.AiOption::getValue).toList());
                }
                node.put("items", items);
            }
            case "ARRAY", "FILE_ARRAY" -> {
                node.put("type", "array");
                Map<String, Object> items = new LinkedHashMap<>();
                if ("FILE_ARRAY".equals(type)) {
                    items.put("type", "string");
                    items.put("description", "文件中心 fileId");
                } else if (field.getItems() != null) {
                    items.put("type", jsonType(field.getItems().getType()));
                    if (StringUtils.isNotBlank(field.getItems().getDescription())) {
                        items.put("description", field.getItems().getDescription());
                    }
                    if (field.getItems().getProperties() != null) {
                        items.put("properties", nestedProperties(field.getItems().getProperties()));
                    }
                } else {
                    items.put("type", "string");
                }
                node.put("items", items);
            }
            case "OBJECT" -> {
                node.put("type", "object");
                node.put("properties", nestedProperties(field.getProperties()));
            }
            case "FILE" -> {
                node.put("type", "string");
                node.put("description", description + "（传文件中心 fileId）");
                return node;
            }
            default -> node.put("type", "string");
        }
        node.put("description", description);
        if (field.getDefaultValue() != null) {
            node.put("default", field.getDefaultValue());
        }
        return node;
    }

    private Map<String, Object> nestedProperties(List<AiFieldDef> fields) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (fields == null) {
            return props;
        }
        for (AiFieldDef child : fields) {
            props.put(child.getKey(), fieldSchema(child));
        }
        return props;
    }

    private String jsonType(String type) {
        String t = StringUtils.upperCase(type);
        return switch (t) {
            case "INTEGER" -> "integer";
            case "NUMBER" -> "number";
            case "BOOLEAN" -> "boolean";
            case "OBJECT" -> "object";
            default -> "string";
        };
    }

    private String describe(AiFieldDef field) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(field.getLabel())) {
            sb.append(field.getLabel());
        }
        if (StringUtils.isNotBlank(field.getDescription())) {
            if (sb.length() > 0) {
                sb.append("：");
            }
            sb.append(field.getDescription());
        }
        if (field.getOptions() != null && !field.getOptions().isEmpty() && field.getOptions().size() <= 20) {
            sb.append("；可选值：");
            sb.append(String.join("/", field.getOptions().stream()
                    .map(AiFieldDef.AiOption::getLabel).toList()));
        }
        if (Boolean.TRUE.equals(field.getRequired())) {
            sb.append("（必填）");
        }
        return sb.toString();
    }
}
