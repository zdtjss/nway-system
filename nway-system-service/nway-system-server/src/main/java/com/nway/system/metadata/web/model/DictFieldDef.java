package com.nway.system.metadata.web.model;

import lombok.Data;

/**
 * 字典字段定义
 */
@Data
public class DictFieldDef {

    /**
     * 字段编码
     */
    private String code;
    /**
     * 字段名称
     */
    private String name;
    /**
     * 字段类型：string/number/date/datetime/boolean
     */
    private String type;
    /**
     * 是否必填
     */
    private Boolean required;
    /**
     * 是否作为字典项查询条件，未开启的字段不出现在字典项查询区
     */
    private Boolean searchable;
    /**
     * 排序
     */
    private Integer sort;
}
