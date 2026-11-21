package com.nway.system.authz.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解。标注在 DAO/Service 方法上，AOP 拦截器自动注入数据范围 WHERE 条件。
 * <p>
 * 用法：
 * <pre>{@code
 * @DataPermission(resource = "contract")
 * public Page<Contract> queryPage(ContractQuery query) { ... }
 * }</pre>
 * <p>
 * appCode 从 SDK 配置（nway.permission.app-code）自动获取，无需注解指定。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {

    /**
     * 资源标识（对应 sys_menu.resource_code / sys_permission_policy.resource_code）
     */
    String resource();

    /**
     * 部门ID字段名（默认 dept_id）
     */
    String deptIdField() default "dept_id";

    /**
     * 部门编码字段名（默认 dept_code，用于 DEPT_WITH_SUB 前缀匹配）
     */
    String deptCodeField() default "dept_code";

    /**
     * 创建人字段名（默认 creator_id，用于 SELF 范围）
     */
    String creatorField() default "creator_id";
}
