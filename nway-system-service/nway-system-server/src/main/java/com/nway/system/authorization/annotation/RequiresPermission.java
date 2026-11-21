package com.nway.system.authorization.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需的权限资源与动作（资源目录的唯一代码事实源）。
 * <p>
 * 可标注在 Controller 类上（该 Controller 默认资源），也可标注在方法上（覆盖类级声明）。
 * 启动时由 {@code ResourceManifestProvider} 扫描并与 {@code sys_resource} 比对：
 * 未登记的资源/动作会在启动期暴露，而不是运行期静默 403。
 * <p>
 * 未标注的接口回退到历史 URL 映射（{@code ApiResourceRegistry}），并在覆盖率报表中
 * 标记为 {@code LEGACY}，便于逐步迁移。
 *
 * <pre>{@code
 * @RequiresPermission(resource = "user")
 * @RestController
 * @RequestMapping("user")
 * public class UserController {
 *
 *     @RequiresPermission(resource = "user", action = "reset")
 *     @PostMapping("resetPassword")
 *     public Result<Void> resetPassword(...) { ... }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** 资源标识（对应 sys_resource.resource_code） */
    String resource();

    /**
     * 动作（对应 sys_resource_action.action）。留空表示沿用历史动作推断
     * （按 URL 末段词根 / HTTP 方法），用于渐进迁移期。
     */
    String action() default "";

    /** 资源所属应用，默认平台内置应用 */
    String appCode() default "nway-system";
}
