package com.nway.system.authz.sdk.client;

/**
 * 内置 ThreadLocal 主体解析器：由接入方在认证过滤器里 {@link #set(String)} / {@link #clear()}。
 */
public class ThreadLocalSubjectResolver implements SubjectResolver {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String employeeId) {
        CURRENT.set(employeeId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    @Override
    public String resolveEmployeeId() {
        return CURRENT.get();
    }
}
