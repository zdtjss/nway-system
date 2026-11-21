package com.nway.system.authz.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需资源与动作（SDK 侧接口级校验）。
 * <p>
 * 未标注的接口不参与接口级授权（与 wform 的显式登记口径一致：只保护声明的端点）。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** 资源标识（对应平台 sys_resource.resource_code） */
    String resource();

    /** 动作（对应 sys_resource_action.action） */
    String action();
}
