package com.nway.system.authorization.service.resource;

import com.nway.system.common.security.PublicApi;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.authorization.web.interceptor.ApiResourceRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 构建接口资源清单（manifest）。
 * <p>
 * 声明来源优先级：
 * <ol>
 *   <li>{@link PublicApi} → 登录态通用接口（不参与资源授权）；</li>
 *   <li>{@link RequiresPermission}（方法级优先于类级）→ 代码即事实；</li>
 *   <li>历史 {@link ApiResourceRegistry}（URL 首段映射）→ 过渡期兜底，报表标记 LEGACY；</li>
 *   <li>都没有 → {@code UNCOVERED}（运行期 fail-closed，启动期按配置阻断）。</li>
 * </ol>
 * 开放平台接口（{@code /open/**}）由 OpenApiInterceptor 独立鉴权，不纳入本清单。
 */
@Component
public class ResourceManifestProvider {

    private static final String PLATFORM_APP_CODE = ApiResourceRegistry.PLATFORM_APP_CODE;

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;

    private volatile ResourceManifest manifest;

    public ResourceManifestProvider(
            @Qualifier("requestMappingHandlerMapping")
            ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider) {
        this.handlerMappingProvider = handlerMappingProvider;
    }

    /** 获取（首次访问时构建）资源清单。 */
    public ResourceManifest get() {
        ResourceManifest current = manifest;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (manifest == null) {
                manifest = build();
            }
            return manifest;
        }
    }

    /** 强制重建（资源变更后刷新）。 */
    public synchronized ResourceManifest rebuild() {
        manifest = build();
        return manifest;
    }

    private ResourceManifest build() {
        RequestMappingHandlerMapping handlerMapping = handlerMappingProvider.getObject();
        List<ResourceManifest.Entry> entries = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            for (String pattern : patternsOf(info)) {
                if (isIgnored(pattern)) {
                    continue;
                }
                entries.add(toEntry(pattern, info, handler));
            }
        });
        return new ResourceManifest(entries);
    }

    private ResourceManifest.Entry toEntry(String pattern, RequestMappingInfo info, HandlerMethod handler) {
        boolean publicApi = handler.hasMethodAnnotation(PublicApi.class)
                || handler.getBeanType().isAnnotationPresent(PublicApi.class);
        if (publicApi) {
            return new ResourceManifest.Entry(pattern, PLATFORM_APP_CODE, null, List.of(),
                    ResourceManifest.Source.PUBLIC);
        }

        RequiresPermission declared = handler.getMethodAnnotation(RequiresPermission.class);
        if (declared == null) {
            declared = handler.getBeanType().getAnnotation(RequiresPermission.class);
        }
        if (declared != null) {
            List<String> actions = StringUtils.hasText(declared.action())
                    ? List.of(declared.action())
                    : ApiResourceRegistry.resolveActions(pattern, firstMethod(info));
            return new ResourceManifest.Entry(pattern, declared.appCode(), declared.resource(),
                    distinct(actions), ResourceManifest.Source.ANNOTATION);
        }

        ApiResourceRegistry.ApiResource legacy = ApiResourceRegistry.resolve(pattern);
        if (legacy == null) {
            return new ResourceManifest.Entry(pattern, null, null, List.of(),
                    ResourceManifest.Source.UNCOVERED);
        }
        return new ResourceManifest.Entry(pattern, legacy.appCode(), legacy.resourceCode(),
                distinct(ApiResourceRegistry.resolveActions(pattern, firstMethod(info))),
                ResourceManifest.Source.LEGACY);
    }

    private static List<String> distinct(List<String> actions) {
        Set<String> unique = new LinkedHashSet<>(actions);
        return List.copyOf(unique);
    }

    private static String firstMethod(RequestMappingInfo info) {
        if (info.getMethodsCondition() == null || info.getMethodsCondition().getMethods().isEmpty()) {
            return null;
        }
        return info.getMethodsCondition().getMethods().iterator().next().name();
    }

    /**
     * 取映射信息中的路径模板。
     * <p>{@code getPatternValues()} 会按当前生效的条件（PathPattern 或 Ant 风格）
     * 统一返回字符串模板；{@code getPatternsCondition()} 自 Spring 7.0 起已标记移除。
     */
    private static Set<String> patternsOf(RequestMappingInfo info) {
        return info.getPatternValues();
    }

    private static boolean isIgnored(String pattern) {
        return pattern.startsWith("/open/")
                || pattern.startsWith("/internal/")
                || pattern.startsWith("/error")
                || pattern.startsWith("/actuator")
                || pattern.startsWith("/v3/api-docs")
                || pattern.startsWith("/swagger-ui")
                || pattern.startsWith("/favicon");
    }
}
