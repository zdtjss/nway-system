package com.nway.system.openplatform.web.model;

import lombok.Data;

import java.util.List;

/**
 * 开放平台：应用资源目录（{@code GET /open/meta/catalog} 响应）。
 * <p>
 * 用途：让调用方（低代码平台等）<b>可发现</b>本应用有哪些资源、每个资源支持哪些动作，
 * 从而把"权限标识符"从"读源码才知道"变成"接口可查询"：
 * <ul>
 *   <li>对接方上报/校验自己声明的资源是否真实存在（启动自检，见 权限模型重构技术方案 §4.2.4）；</li>
 *   <li>对外文档与 SDK 可据此自动生成，开发者不再靠猜字符串。</li>
 * </ul>
 */
@Data
public class OpenResourceCatalogVo {

    /** 应用编码 */
    private String appCode;

    /** 资源列表 */
    private List<Resource> resources;

    /**
     * 一个资源及其可用动作。
     */
    @Data
    public static class Resource {

        /** 资源标识（权限策略 {@code resource_code} 取值） */
        private String resourceCode;

        /** 资源名称（人可读） */
        private String name;

        /** 可用动作；空列表表示资源存在但未登记动作 */
        private List<Action> actions;
    }

    /**
     * 一个动作。
     */
    @Data
    public static class Action {

        /** 动作标识（权限策略 {@code actions} 取值） */
        private String action;

        /** 动作名称（人可读） */
        private String name;
    }
}