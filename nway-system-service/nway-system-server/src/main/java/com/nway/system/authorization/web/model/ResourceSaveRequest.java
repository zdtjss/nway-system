package com.nway.system.authorization.web.model;

import lombok.Data;

import java.util.List;

/**
 * 资源保存请求（新增或更新 + 替换动作）。
 */
@Data
public class ResourceSaveRequest {

    private String id;

    private String appCode;

    private String resourceCode;

    /** PAGE/API/CAPABILITY/DATA/FIELD，缺省 PAGE */
    private String resourceType;

    private String name;

    private String description;

    /** PLATFORM/SHARED/TENANT，缺省 TENANT */
    private String tenantScope;

    /** 资源归属租户（TENANT/SHARED 必填，缺省取当前租户） */
    private String ownerTenantCode;

    /** ACTIVE/DEPRECATED */
    private String status;

    /** 动作列表；为 null 表示不修改动作 */
    private List<ActionItem> actions;

    @Data
    public static class ActionItem {
        private String action;
        private String label;
    }
}
