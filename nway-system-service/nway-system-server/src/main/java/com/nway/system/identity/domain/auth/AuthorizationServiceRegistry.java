package com.nway.system.identity.domain.auth;

import com.nway.system.identity.domain.enums.LoginTypeEnum;
import com.nway.system.common.exception.NwaySystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 登录方式策略注册表。
 * <p>
 * 启动时收集全部 {@link AuthorizationService}，按登录方式建立索引并校验唯一性（fail-fast），
 * 替代原先按 bean 名称父串拼装查找的方式。
 * <p>
 * 扩展指引（微信 / 企业微信等）：
 * <ol>
 *   <li>{@link LoginTypeEnum} 登记登录方式；</li>
 *   <li>新增 {@link AuthorizationService} 实现并声明 {@link AuthorizationService#loginType()}；</li>
 *   <li>若身份由外部系统校验，复用 {@code ExternalUserProvisioner} 完成外部身份到平台用户的映射。</li>
 * </ol>
 * 登录主流程、登录页协议均无需调整。
 */
@Component
public class AuthorizationServiceRegistry {

    private final Map<LoginTypeEnum, AuthorizationService> services;

    @Autowired
    public AuthorizationServiceRegistry(List<AuthorizationService> authorizationServices) {
        Map<LoginTypeEnum, AuthorizationService> registry = new EnumMap<>(LoginTypeEnum.class);
        for (AuthorizationService service : authorizationServices) {
            AuthorizationService existing = registry.putIfAbsent(service.loginType(), service);
            if (existing != null) {
                throw new IllegalStateException("登录方式重复注册：" + service.loginType()
                        + "（" + existing.getClass().getName() + " / " + service.getClass().getName() + "）");
            }
        }
        this.services = Collections.unmodifiableMap(registry);
    }

    /**
     * 按登录方式获取认证实现。
     *
     * @param loginType 登录方式编码（兼容旧值 external，等同 password）
     * @return 对应认证实现
     */
    public AuthorizationService get(String loginType) {
        LoginTypeEnum type = LoginTypeEnum.of(loginType);
        AuthorizationService service = services.get(type);
        if (service == null) {
            throw new NwaySystemException("暂不支持的登录方式：" + loginType);
        }
        return service;
    }
}
