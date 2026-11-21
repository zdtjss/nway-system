package com.nway.system.authorization.web.model;

import com.nway.system.common.web.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据权限规则分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataPermissionQuery extends PageParam {

    /**
     * 主体类型：ROLE/USER/POSITION
     */
    private String subjectType;

    /**
     * 主体id（角色id/工号/岗位id）
     */
    private String subjectId;

    /**
     * 业务代号
     */
    private String bizCode;

    /**
     * 资源标识
     */
    private String resourceCode;

    /**
     * 数据范围
     */
    private String scopeType;
}
