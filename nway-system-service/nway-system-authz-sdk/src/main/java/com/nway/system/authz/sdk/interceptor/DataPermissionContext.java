package com.nway.system.authz.sdk.interceptor;

import com.nway.system.authz.sdk.filter.PermissionFilter;

/**
 * 数据权限 ThreadLocal 上下文。
 * <p>
 * AOP 拦截器构建 WHERE 条件后存入此上下文，DAO 层在构建 SQL 时读取并追加。
 */
public final class DataPermissionContext {

    private static final ThreadLocal<PermissionFilter.SqlFragment> HOLDER = new ThreadLocal<>();

    private DataPermissionContext() {
    }

    public static void set(PermissionFilter.SqlFragment fragment) {
        HOLDER.set(fragment);
    }

    public static PermissionFilter.SqlFragment getWhereSql() {
        return HOLDER.get();
    }

    public static boolean hasContext() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
