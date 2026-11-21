package com.nway.system.metadata.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 字典项
 */
@Data
@Table("sys_dict_item")
@EqualsAndHashCode(callSuper = false)
public class DictItem extends BaseEntity {

    private String dictTypeId;
    /**
     * 字典项值JSON：传统模式 {"key":"using","value":"启用"}，自定义字段模式 {"employee_id":"001","name":"张三"}
     */
    private String itemValue;
    private Integer sort;
    private String status;
}
