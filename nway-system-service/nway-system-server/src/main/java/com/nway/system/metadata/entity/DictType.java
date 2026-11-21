package com.nway.system.metadata.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 字典类别（通用多字段类字典）
 */
@Data
@Table("sys_dict_type")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class DictType extends BaseEntity {

    private String dictCode;
    private String dictName;
    private String remark;
    /**
     * 字段定义JSON，格式 [{"code":"employee_id","name":"工号","type":"string","required":true,"sort":1}]
     */
    private String fieldDefs;
    private String status;
}
