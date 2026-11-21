package com.nway.system.authorization.service.policy;

import com.nway.system.authorization.domain.MergedPolicy;

import java.util.Map;

/**
 * 策略加载器接口。
 * <p>
 * 用于解除 PolicyCacheManager → PolicyService 的循环依赖。
 * PolicyCacheManager 依赖此接口，PolicyService 实现此接口。
 */
public interface PolicyLoader {

    /**
     * 从数据库加载并合并用户的全部权限策略。
     *
     * @param employeeId 工号
     * @return appCode:resourceCode → MergedPolicy 映射
     */
    Map<String, MergedPolicy> loadFromDb(String employeeId);
}
