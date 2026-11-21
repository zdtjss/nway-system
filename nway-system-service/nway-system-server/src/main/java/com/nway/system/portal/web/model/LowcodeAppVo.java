package com.nway.system.portal.web.model;

import com.nway.system.portal.entity.App;
import lombok.Data;

/**
 * 低代码平台可发布应用信息。
 */
@Data
public class LowcodeAppVo {

    /** 应用标识 */
    private String appCode;

    /** 应用名称 */
    private String appName;

    /** 前端部署地址（低代码页面路由路径相对该 host 拼接） */
    private String host;

    public static LowcodeAppVo from(com.nway.system.portal.entity.App app) {
        LowcodeAppVo vo = new LowcodeAppVo();
        vo.setAppCode(app.getAppCode());
        vo.setAppName(app.getAppName());
        vo.setHost(app.getHost());
        return vo;
    }
}
