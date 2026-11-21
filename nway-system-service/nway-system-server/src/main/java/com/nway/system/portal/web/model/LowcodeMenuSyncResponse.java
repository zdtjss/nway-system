package com.nway.system.portal.web.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 低代码平台菜单同步响应。
 */
@Data
public class LowcodeMenuSyncResponse {

    /** 逐项落库回执 */
    private List<ResultItem> results = new ArrayList<>();

    /** 非阻断性告警（如自动授权时角色不存在、跨租户被拒等） */
    private List<String> warnings = new ArrayList<>();

    public void addResult(String externalId, String menuId, String parentId, String status) {
        ResultItem item = new ResultItem();
        item.setExternalId(externalId);
        item.setMenuId(menuId);
        item.setParentId(parentId);
        item.setStatus(status);
        this.results.add(item);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    /**
     * 单项落库回执。parentId 为解析后的最终父菜单id，供调用方核对挂载结果。
     */
    @Data
    public static class ResultItem {

        public static final String CREATED = "CREATED";
        public static final String UPDATED = "UPDATED";

        /** 低代码侧唯一标识 */
        private String externalId;

        /** 本系统菜单id */
        private String menuId;

        /** 解析后的最终父菜单id（顶级为空串） */
        private String parentId;

        /** CREATED / UPDATED */
        private String status;
    }
}
