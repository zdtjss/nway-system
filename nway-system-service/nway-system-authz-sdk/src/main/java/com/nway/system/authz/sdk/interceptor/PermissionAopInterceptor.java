package com.nway.system.authz.sdk.interceptor;

import com.nway.system.authz.sdk.annotation.DataPermission;
import com.nway.system.authz.sdk.client.CurrentUserResolver;
import com.nway.system.authz.sdk.client.PermissionPolicyClient;
import com.nway.system.authz.sdk.filter.PermissionFilter;
import com.nway.system.authz.sdk.model.ResourcePolicy;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Map;

/**
 * 权限 AOP 拦截器（nway-jdbc / 通用 DAO）。
 * <p>
 * 拦截 {@link DataPermission} 注解标注的方法，在执行前构建数据范围 WHERE 条件
 * 并存入 {@link DataPermissionContext}，方法执行后清理。
 */
@Slf4j
@Aspect
public class PermissionAopInterceptor {

    private final PermissionPolicyClient policyClient;
    private final CurrentUserResolver userResolver;

    public PermissionAopInterceptor(PermissionPolicyClient policyClient,
                                    CurrentUserResolver userResolver) {
        this.policyClient = policyClient;
        this.userResolver = userResolver;
    }

    @Around("@annotation(dataPermission)")
    public Object around(ProceedingJoinPoint joinPoint, DataPermission dataPermission) throws Throwable {
        PermissionFilter.SqlFragment previous = DataPermissionContext.getWhereSql();
        try {
            Map<String, Object> userVars = userResolver != null ? userResolver.resolve() : null;
            if (userVars == null) {
                return joinPoint.proceed();
            }

            Object employeeIdVal = userVars.get("user.employeeId");
            if (employeeIdVal == null) {
                return joinPoint.proceed();
            }

            String employeeId = employeeIdVal.toString();
            String resourceCode = dataPermission.resource();

            // 获取策略
            ResourcePolicy policy = policyClient.getPolicy(employeeId, resourceCode);

            // 构建 WHERE 条件
            PermissionFilter.FieldMapping mapping = new PermissionFilter.FieldMapping(
                    dataPermission.deptIdField(),
                    dataPermission.deptCodeField(),
                    dataPermission.creatorField()
            );

            PermissionFilter.SqlFragment where = PermissionFilter.buildDataScopeWhere(policy, mapping, userVars);
            if (where != null) {
                DataPermissionContext.set(where);
            }

            return joinPoint.proceed();
        } finally {
            if (previous != null) {
                DataPermissionContext.set(previous);
            } else {
                DataPermissionContext.clear();
            }
        }
    }
}
