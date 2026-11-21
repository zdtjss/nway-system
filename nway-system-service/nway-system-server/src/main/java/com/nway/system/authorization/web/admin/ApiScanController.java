package com.nway.system.authorization.web.admin;

import com.nway.system.authorization.service.resource.ResourceService;
import com.nway.system.common.security.PublicApi;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.common.web.Result;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * API 注册扫描与资源覆盖报表（登记为 api_scan 资源）。
 * <p>
 * {@code apiScan}：历史接口清单（仅路径与是否登录态通用）；
 * {@code resourceCoverage}：升级后的覆盖报表——每条映射的资源声明来源（注解/历史/未覆盖）
 * 以及与 {@code sys_resource} / {@code sys_resource_action} 的登记比对结果。
 */
@RequiresPermission(resource = "api_scan")
@RestController
@RequestMapping("system")
public class ApiScanController {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private ResourceService resourceService;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 资源覆盖报表：声明来源 + 资源/动作登记结果
     */
    @GetMapping("resourceCoverage")
    public Result<ResourceService.CoverageReport> resourceCoverage() {
        return Result.ok(resourceService.coverage());
    }

    /**
     * 扫描所有已注册 API 接口（历史接口，保留兼容）
     */
    @GetMapping("apiScan")
    public Result<List<ApiInfo>> scanRegisteredApis() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        List<ApiInfo> apiList = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            ApiInfo info = new ApiInfo();

            // 获取路径
            if (mappingInfo.getPathPatternsCondition() != null) {
                String path = mappingInfo.getPathPatternsCondition().getPatterns().stream()
                        .findFirst()
                        .map(Object::toString)
                        .orElse("");
                // 去掉 contextPath 前缀
                if (path.startsWith(contextPath)) {
                    path = path.substring(contextPath.length());
                }
                info.setPath(path);
            }

            // 获取 HTTP 方法
            info.setMethods(mappingInfo.getMethodsCondition().getMethods().stream()
                    .map(Enum::name)
                    .sorted()
                    .toList());

            // 获取所在类和方法
            info.setClassName(handlerMethod.getBeanType().getSimpleName());
            info.setMethodName(handlerMethod.getMethod().getName());

            // 判断是否标记 @PublicApi
            boolean isPublic = handlerMethod.hasMethodAnnotation(PublicApi.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class);
            info.setPublicApi(isPublic);

            apiList.add(info);
        }

        apiList.sort(Comparator.comparing(ApiInfo::getPath));
        return Result.ok(apiList);
    }

    @Data
    public static class ApiInfo {
        private String path;
        private List<String> methods;
        private String className;
        private String methodName;
        private boolean publicApi;
    }
}
