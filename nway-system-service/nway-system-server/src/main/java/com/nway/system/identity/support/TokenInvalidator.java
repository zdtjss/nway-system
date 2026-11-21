package com.nway.system.identity.support;

/**
 * Token 失效器接口。
 * <p>
 * 用于解耦用户管理与认证会话：管理操作（禁用用户、强制下线）通过此接口
 * 吊销 Token，实现只依赖缓存组件，不反向依赖认证/用户服务。
 * 默认实现：{@link DefaultTokenInvalidator}。
 *
 * @author nway
 */
public interface TokenInvalidator {

    /**
     * 使指定用户的所有 Token 失效（强制下线）。
     *
     * @param employeeId 工号
     */
    void invalidateUserTokens(String employeeId);
}
