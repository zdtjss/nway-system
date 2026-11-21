package com.nway.system.authorization.service.resource;

import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import com.nway.system.authorization.entity.SysResourceAction;
import com.nway.system.authorization.web.interceptor.ApiResourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 接口资源清单启动校验。
 * <p>
 * 与 wform 的 {@code PermissionMappingVerifier} 同思路：把"接口声明了但资源目录没登记"
 * 这类配置错误挡在启动期，而不是运行期静默 403。
 * <p>
 * 配置：
 * <ul>
 *   <li>{@code nway.authz.catalog-check}：FAIL_FAST（默认）/ WARN / OFF；</li>
 *   <li>{@code nway.authz.require-annotations}：true 时要求所有受控接口都已用
 *       {@code @RequiresPermission} 声明（默认 false，迁移期容忍 LEGACY）。</li>
 * </ul>
 * 数据库不可达时只告警不阻断（降级原则 P1）。
 */
@Component
@Order(1)
public class ResourceManifestValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ResourceManifestValidator.class);

    private final ResourceManifestProvider manifestProvider;
    private final ResourceDao resourceDao;

    @Value("${nway.authz.catalog-check:FAIL_FAST}")
    private String catalogCheck;

    @Value("${nway.authz.require-annotations:false}")
    private boolean requireAnnotations;

    public ResourceManifestValidator(ResourceManifestProvider manifestProvider, ResourceDao resourceDao) {
        this.manifestProvider = manifestProvider;
        this.resourceDao = resourceDao;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResourceManifest manifest = manifestProvider.rebuild();
        log.info("接口资源清单：{} 条映射（注解 {} / 历史 {} / 公共 {} / 未覆盖 {}），注解覆盖率 {}%",
                manifest.entries().size(),
                manifest.count(ResourceManifest.Source.ANNOTATION),
                manifest.count(ResourceManifest.Source.LEGACY),
                manifest.count(ResourceManifest.Source.PUBLIC),
                manifest.count(ResourceManifest.Source.UNCOVERED),
                Math.round(manifest.annotationCoverage() * 100));

        List<String> problems = new ArrayList<>();
        for (ResourceManifest.Entry entry : manifest.entries()) {
            if (entry.source() == ResourceManifest.Source.UNCOVERED) {
                problems.add("未声明资源: " + entry.pattern()
                        + "（请标注 @RequiresPermission 或在 ApiResourceRegistry 登记）");
            } else if (requireAnnotations && entry.source() == ResourceManifest.Source.LEGACY) {
                problems.add("仍使用历史 URL 映射: " + entry.pattern() + " → "
                        + entry.appCode() + ":" + entry.resourceCode());
            }
        }
        problems.addAll(checkAgainstCatalog(manifest));

        if (problems.isEmpty()) {
            return;
        }
        String msg = "接口资源清单校验未通过：\n  " + String.join("\n  ", problems);
        if ("OFF".equalsIgnoreCase(catalogCheck)) {
            log.debug("接口资源清单校验已关闭（catalog-check=OFF），发现 {} 项问题", problems.size());
            return;
        }
        if ("WARN".equalsIgnoreCase(catalogCheck)) {
            log.warn(msg);
            return;
        }
        throw new IllegalStateException(msg);
    }

    /**
     * 与 sys_resource / sys_resource_action 比对。库不可达（迁移未执行/连接异常）时只告警。
     */
    private List<String> checkAgainstCatalog(ResourceManifest manifest) {
        Set<String> keys = new LinkedHashSet<>();
        for (ResourceManifest.Entry entry : manifest.entries()) {
            if (entry.resourceCode() != null && entry.source() != ResourceManifest.Source.PUBLIC) {
                keys.add(entry.appCode() + ":" + entry.resourceCode());
            }
        }
        List<String> problems = new ArrayList<>();
        Map<String, SysResource> resources = new LinkedHashMap<>();
        try {
            for (String key : keys) {
                String[] parts = key.split(":", 2);
                SysResource resource = resourceDao.getByAppAndCode(parts[0], parts[1]);
                if (resource == null) {
                    problems.add("资源未在 sys_resource 登记: " + key);
                } else {
                    resources.put(key, resource);
                }
            }
            if (!resources.isEmpty()) {
                Map<String, Set<String>> registered = new LinkedHashMap<>();
                for (SysResourceAction action : resourceDao.listActions(
                        resources.values().stream().map(SysResource::getId).toList())) {
                    registered.computeIfAbsent(action.getResourceId(), k -> new LinkedHashSet<>())
                            .add(action.getAction());
                }
                for (ResourceManifest.Entry entry : manifest.entries()) {
                    if (entry.actions().isEmpty() || entry.resourceCode() == null) {
                        continue;
                    }
                    SysResource resource = resources.get(entry.appCode() + ":" + entry.resourceCode());
                    if (resource == null) {
                        continue;
                    }
                    Set<String> actions = registered.getOrDefault(resource.getId(), Set.of());
                    for (String declared : entry.actions()) {
                        if (!actions.contains(declared)) {
                            problems.add("动作未在 sys_resource_action 登记: "
                                    + entry.appCode() + ":" + entry.resourceCode() + ":" + declared);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("接口资源清单未能与数据库比对（迁移未执行或数据库不可用），仅输出内存清单。原因={}",
                    e.getMessage());
            return List.of();
        }
        return problems;
    }
}
