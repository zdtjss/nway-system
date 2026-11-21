package com.nway.system.identity.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位
 */
@Data
@Table("sys_position")
@EqualsAndHashCode(callSuper = false)
public class SysPosition extends BaseEntity {

    private String positionCode;
    private String positionName;
    /** 所属部门（岗位唯一归属组织，非空） */
    private String deptId;
    private String positionLevel;
    private String status;
    private String remark;
}
