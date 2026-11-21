package com.nway.system.identity.web.model;

import com.nway.system.common.web.PageParam;
import lombok.Data;

/**
 * 岗位分页查询条件
 */
@Data
public class PositionQuery extends PageParam {
    /**
     * 状态筛选（using/disabled），为空时查询全部
     */
    private String status;
    /**
     * 岗位编码（模糊匹配），为空不参与筛选
     */
    private String positionCode;
    /**
     * 岗位名称（模糊匹配），为空不参与筛选
     */
    private String positionName;
}