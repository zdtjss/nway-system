package com.nway.system;

import com.nway.system.common.security.PublicApi;
import com.nway.system.authorization.annotation.RequiresPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端接口必须显式声明资源（{@link RequiresPermission}），否则只能标记为 {@link PublicApi}。
 * <p>
 * 这是"资源代码化"的守门测试：新增 Controller 忘记声明时构建失败，而不是等到运行期 403。
 */
class ResourceAnnotationCoverageTest {

    private static final String BASE_PACKAGE = "com.nway.system";
    private static final String OPEN_PLATFORM_PACKAGE = "com.nway.system.openplatform.web.open";
    private static final String INTERNAL_PACKAGE = "com.nway.system.message.web.internal";

    @Test
    @DisplayName("管理端 Controller 全部声明了资源或显式标记为公共接口")
    void allAdminControllersDeclareResource() throws ClassNotFoundException {
        List<String> missing = new ArrayList<>();
        for (Class<?> type : scanRestControllers()) {
            if (isOpenPlatform(type) || isInternal(type)) {
                continue;
            }
            boolean declared = type.isAnnotationPresent(RequiresPermission.class)
                    || type.isAnnotationPresent(PublicApi.class);
            if (!declared) {
                missing.add(type.getSimpleName());
            }
        }
        assertThat(missing)
                .as("以下 Controller 未声明 @RequiresPermission 或 @PublicApi")
                .isEmpty();
    }

    @Test
    @DisplayName("声明的资源标识符合命名规范")
    void resourceCodesAreWellFormed() throws ClassNotFoundException {
        for (Class<?> type : scanRestControllers()) {
            RequiresPermission annotation = type.getAnnotation(RequiresPermission.class);
            if (annotation != null) {
                assertThat(annotation.resource())
                        .as(type.getSimpleName() + " 的资源标识")
                        .matches("^[A-Za-z][A-Za-z0-9_:.\\-]{0,49}$");
            }
        }
    }

    private static List<Class<?>> scanRestControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> types = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            types.add(Class.forName(definition.getBeanClassName()));
        }
        return types;
    }

    private static boolean isOpenPlatform(Class<?> type) {
        return type.getPackageName().startsWith(OPEN_PLATFORM_PACKAGE);
    }

    private static boolean isInternal(Class<?> type) {
        return type.getPackageName().startsWith(INTERNAL_PACKAGE);
    }
}
