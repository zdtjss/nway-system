package com.nway.system.authz.sdk.client;

/**
 * 当前请求主体解析 SPI（由接入方实现，或使用内置 ThreadLocal 实现）。
 */
public interface SubjectResolver {

    /** 当前请求的工号；无认证上下文返回 null。 */
    String resolveEmployeeId();
}
