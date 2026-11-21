package com.nway.system.portal.web.model;

import lombok.Data;

import java.util.List;

/**
 * 低代码平台菜单删除/停用请求。
 */
@Data
public class LowcodeMenuOperateRequest {

    /** 目标应用标识 */
    private String appCode;

    /** 待操作的菜单外部标识列表 */
    private List<String> externalIds;
}
