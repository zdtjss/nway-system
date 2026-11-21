package com.nway.system.identity.support;

import com.nway.system.identity.service.TokenVersionPubSubService;
import com.nway.system.identity.service.UserStatusCache;
import com.nway.system.authorization.service.policy.PolicyCacheManager;
import com.nway.system.identity.dao.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Token 失效器默认实现。
 * <p>
 * JWT 无状态无法直接吊销，通过 Token 版本号机制使已签发 Token 失效：
 * <ul>
 *   <li>自增数据库 token_version</li>
 *   <li>清除版本号缓存（迫使拦截器从 DB 加载新版本号）</li>
 *   <li>清除权限缓存</li>
 *   <li>通过 Redis Pub/Sub 广播版本号变更（ACTION_LOGOUT），各实例立即刷新本地缓存</li>
 * </ul>
 * <p>
 * 重要：本方法语义仅为“强制重新登录”（改密/重置密码/下线），
 * <b>不会</b>将用户置为禁用。禁用账号由 {@code UserService.disableUser} 单独处理，
 * 避免“强制下线”误写 {@code nway_user_status=disabled} 缓存导致登录被误判为“账号已被锁定”。
 * <p>
 * 仅依赖缓存组件和 DAO，不引入认证/用户服务，保证依赖图单向。
 *
 * @author nway
 */
@Component
public class DefaultTokenInvalidator implements TokenInvalidator {

    @Autowired
    private PolicyCacheManager policyCacheManager;

    @Autowired
    private UserStatusCache userStatusCache;

    @Autowired
    private UserDao userDao;

    @Autowired
    private TokenVersionPubSubService tokenVersionPubSubService;

    @Override
    public void invalidateUserTokens(String employeeId) {
        // 自增 token 版本号，使所有已签发 JWT 失效
        userDao.incrementTokenVersion(employeeId);
        userStatusCache.evictTokenVersion(employeeId);
        // 清除权限缓存（不标记禁用：强制下线 ≠ 禁用账号）
        policyCacheManager.evict(employeeId);
        // 广播版本号变更（下线语义），各实例立即失效本地 token version 缓存
        tokenVersionPubSubService.publishVersionChange(employeeId, TokenVersionPubSubService.ACTION_LOGOUT);
    }
}
