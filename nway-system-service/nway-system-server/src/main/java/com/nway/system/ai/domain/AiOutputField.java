package com.nway.system.ai.domain;

import lombok.Data;

import java.util.List;

/**
 * 出参字段定义（对应 ai-interface.schema.json outputField）。
 *
 * @author nway
 */
@Data
public class AiOutputField {

    private String key;

    /** 参数中文名 */
    private String label;

    /** JSONPath，如 $.data.id；省略时按 $.{key} 取值 */
    private String path;

    private String type;

    private String description;

    private Boolean sensitive;

    /** 数组元素结构，仅用于元数据说明与展示，运行时不逐字段映射 */
    private AiOutputItems items;

    /** 对象/数组元素的子字段说明，仅用于元数据说明与展示 */
    private List<AiOutputField> properties;

    @Data
    public static class AiOutputItems {
        private String type;
        private String description;
        private List<AiOutputField> properties;
    }
}
