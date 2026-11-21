package com.nway.system.authorization.service.resource;

import com.nway.system.identity.web.admin.UserController;
import com.nway.system.common.security.PublicApi;
import com.nway.system.authorization.annotation.RequiresPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 接口资源清单构建：注解声明优先、公共接口识别、未声明标记、路径变量匹配。
 */
class ResourceManifestProviderTest {

    @Test
    @DisplayName("类级注解：资源取自注解，动作按历史规则推断")
    void classLevelAnnotation() throws Exception {
        ResourceManifest manifest = build(
                entry("/user/save", "POST", UserController.class, "save"),
                entry("/user/queryPage", "GET", UserController.class, "queryPage"));

        ResourceManifest.Entry save = manifest.resolve("/user/save").orElseThrow();
        assertThat(save.source()).isEqualTo(ResourceManifest.Source.ANNOTATION);
        assertThat(save.resourceCode()).isEqualTo("user");
        assertThat(save.actions()).containsExactly("create", "edit");

        ResourceManifest.Entry query = manifest.resolve("/user/queryPage").orElseThrow();
        assertThat(query.actions()).containsExactly("view");
    }

    @Test
    @DisplayName("方法级注解覆盖类级：显式动作优先")
    void methodLevelOverridesAction() throws Exception {
        ResourceManifest manifest = build(
                entry("/user/resetPassword", "POST", UserController.class, "resetPassword"));

        ResourceManifest.Entry entry = manifest.resolve("/user/resetPassword").orElseThrow();
        assertThat(entry.resourceCode()).isEqualTo("user");
        assertThat(entry.actions()).containsExactly("reset");
    }

    @Test
    @DisplayName("PublicApi：识别为公共接口，不参与资源授权")
    void publicApiEntry() throws Exception {
        ResourceManifest manifest = build(
                entry("/login", "POST", PublicController.class, "login"));

        ResourceManifest.Entry entry = manifest.resolve("/login").orElseThrow();
        assertThat(entry.source()).isEqualTo(ResourceManifest.Source.PUBLIC);
        assertThat(entry.covered()).isFalse();
    }

    @Test
    @DisplayName("未声明且无历史映射：标记 UNCOVERED，运行期 fail-closed")
    void uncoveredEntry() throws Exception {
        ResourceManifest manifest = build(
                entry("/unknown/endpoint", "GET", PlainController.class, "plain"));

        ResourceManifest.Entry entry = manifest.resolve("/unknown/endpoint").orElseThrow();
        assertThat(entry.source()).isEqualTo(ResourceManifest.Source.UNCOVERED);
        assertThat(entry.covered()).isFalse();
    }

    @Test
    @DisplayName("路径变量映射：按模板匹配实际请求路径")
    void pathVariableMatching() throws Exception {
        ResourceManifest manifest = build(
                entry("/data/get/{formName}", "GET", PlainController.class, "plain"));

        assertThat(manifest.resolve("/data/get/order")).isPresent();
        assertThat(manifest.resolve("/data/get/order/extra")).isEmpty();
    }

    @Test
    @DisplayName("注解覆盖率：公共接口不计入分母")
    void annotationCoverage() throws Exception {
        ResourceManifest manifest = build(
                entry("/user/save", "POST", UserController.class, "save"),
                entry("/login", "POST", PublicController.class, "login"));

        assertThat(manifest.annotationCoverage()).isEqualTo(1.0);
        assertThat(manifest.count(ResourceManifest.Source.PUBLIC)).isEqualTo(1);
    }

    // ====== 辅助 ======

    private static Map.Entry<RequestMappingInfo, HandlerMethod> entry(String pattern, String httpMethod,
                                                                      Class<?> beanType, String methodName)
            throws Exception {
        Method method = findMethod(beanType, methodName);
        RequestMappingInfo info = RequestMappingInfo.paths(pattern)
                .methods(org.springframework.web.bind.annotation.RequestMethod.valueOf(httpMethod))
                .build();
        return Map.entry(info, new HandlerMethod(beanType.getDeclaredConstructor().newInstance(), method));
    }

    private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static ResourceManifest build(Map.Entry<RequestMappingInfo, HandlerMethod>... entries) throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : entries) {
            handlers.put(entry.getKey(), entry.getValue());
        }
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        when(mapping.getHandlerMethods()).thenReturn(handlers);
        ObjectProvider<RequestMappingHandlerMapping> provider = new ObjectProvider<>() {
            @Override
            public RequestMappingHandlerMapping getObject() {
                return mapping;
            }
        };
        return new ResourceManifestProvider(provider).get();
    }

    // ====== 测试用 Controller ======

    @RequiresPermission(resource = "user")
    @RequestMapping("user")
    static class UserController {
        @PostMapping("save")
        public void save() {
        }

        @GetMapping("queryPage")
        public void queryPage() {
        }

        @RequiresPermission(resource = "user", action = "reset")
        @PostMapping("resetPassword")
        public void resetPassword() {
        }
    }

    @PublicApi(reason = "登录")
    @RequestMapping("login")
    static class PublicController {
        @PostMapping
        public void login() {
        }
    }

    static class PlainController {
        @GetMapping("/unknown/endpoint")
        public void plain() {
        }
    }
}
