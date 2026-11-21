package com.nway.system.identity.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 部门信息
 */
@Data
@Table("sys_dept")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class SysDept extends BaseEntity {

    private String deptCode;
    private String deptName;
    private String parentId;
    private Integer sort;
    private String status;
}
