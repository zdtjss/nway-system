package com.nway.system.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记登录用户均可访问、无需菜单权限校验的接口。
 * <p>
 * 区别于 {@code url.anonymous}（匿名访问，不校验登录），本注解用于登录后免菜单权限校验的场景，
 * 例如获取当前用户菜单、消息轮询、修改个人密码等。
 * <p>
 * 可标注在方法或类上，类级别标注对类内所有方法生效。
 *
 * @author nway
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {

    /**
     * 说明，便于维护时理解为何免菜单权限校验。
     */
    String reason() default "";
}
