package com.nway.system.metadata.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统配置
 */
@Data
@Table("sys_config")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class SysConfig extends BaseEntity {

    private String configKey;
    private String configValue;
    private String configName;
    private String configGroup;
    private String remark;
    private String status;
}
