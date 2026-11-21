package com.nway.system.authz.sdk.model;

import java.util.List;

/**
 * 平台下发的策略包（版本化）。
 *
 * @param version    全局授权版本（单调递增，作为本地缓存失效依据）
 * @param appCode    应用编码
 * @param resources  资源授权列表
 */
public record AuthzBundle(long version, String appCode, List<ResourceGrant> resources) {

    public static final String ALL_ACTIONS = "*";
    public static final String WILDCARD_RESOURCE = "*";

    public record ResourceGrant(String resourceCode, List<String> actions, List<DataPredicate> predicates) {
    }

    /**
     * 数据谓词：由平台决策内核下发，消费方只翻译为 SQL/查询条件，不再自行推导。
     */
    public record DataPredicate(String kind, List<String> values) {

        public static final String ALL = "ALL";
        public static final String SELF = "SELF";
        public static final String DEPT = "DEPT";
        public static final String DEPT_TREE = "DEPT_TREE";
        public static final String DEPT_LIST = "DEPT_LIST";
        public static final String NONE = "NONE";
    }
}
