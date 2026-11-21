package com.nway.system.authz.sdk.client;

import java.util.Map;

/**
 * 当前用户信息解析器（SPI，由子应用实现）。
 * <p>
 * SDK 的 AOP 拦截器通过此接口获取当前请求的用户信息，用于：
 * <ul>
 *   <li>获取 employeeId 调用平台策略接口</li>
 *   <li>为 SELF 范围提供 user.employeeId 变量</li>
 * </ul>
 * <p>
 * 返回的 Map 至少包含 {@code user.employeeId}。
 */
public interface CurrentUserResolver {

    /**
     * 解析当前请求的用户变量上下文。
     *
     * @return 变量 Map（至少含 user.employeeId），无用户上下文时返回 null
     */
    Map<String, Object> resolve();
}
