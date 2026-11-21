package com.nway.system.metadata.web.model;

import lombok.Data;

import java.util.Map;

@Data
public class DictItemQuery {

    /**
     * 字典类别id
     */
    private String dictTypeId;
    /**
     * 字段过滤条件，key为字段编码（传统模式为保留编码 key/value），value为过滤值
     */
    private Map<String, String> fieldFilters;
    /**
     * 状态
     */
    private String status;
}
