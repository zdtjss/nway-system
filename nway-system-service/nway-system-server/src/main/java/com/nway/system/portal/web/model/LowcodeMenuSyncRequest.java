package com.nway.system.portal.web.model;

import lombok.Data;

import java.util.List;

/**
 * 低代码平台菜单同步请求。
 * <p>
 * 以 externalId 为幂等键按批 upsert：批内或历史批次已存在的同 externalId 菜单做更新，
 * 否则新建。不删除批次外的既有菜单（非全量替换，目标应用可能混有平台管理员配置的菜单）。
 */
@Data
public class LowcodeMenuSyncRequest {

    /** 目标应用标识（菜单挂载的应用，须为代理用户租户下的应用） */
    private String appCode;

    /** 发布后自动授权的角色编码列表（为本次涉及的可见页面创建 view 权限策略，可选） */
    private List<String> grantRoleCodes;

    /** 菜单项列表（目录/页面，支持整树或增量发布） */
    private List<LowcodeMenuSyncItem> items;
}
