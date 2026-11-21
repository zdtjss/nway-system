package com.nway.system.portal.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 应用（微前端子应用）。
 * <p>
 * 替代原 sys_business，每条记录对应一个微前端子应用。
 * appCode 是子应用的唯一标识，用于权限策略中的 appCode 维度定位。
 */
@Data
@Table("sys_app")
public class App extends BaseEntity {

    /** 应用标识（全局唯一） */
    private String appCode;

    /** 应用名称 */
    private String appName;

    /** 前端部署地址（微前端加载入口） */
    private String host;

    /** 后端 API 基础地址 */
    private String apiBaseUrl;

    /** 状态：using/disabled */
    private String status;

    /** 排序 */
    private Integer sort;
}
