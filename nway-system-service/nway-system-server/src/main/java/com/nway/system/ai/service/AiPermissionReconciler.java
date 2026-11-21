package com.nway.system.ai.service;

import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.ai.dao.AiInterfaceDao;
import com.nway.system.ai.entity.AiInterface;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 接口权限资源对账：启动时补齐接口资源、下线无主资源。
 * <p>
 * 解决两类存量问题：
 * <ul>
 *   <li>接口改名/删除后旧的 {@code ai:xxx} 资源仍为 ACTIVE，授权页出现“幽灵资源”；</li>
 *   <li>资源名称/租户作用域与接口配置不一致。</li>
 * </ul>
 *
 * @author nway
 */
@Component
@Order(1)
public class AiPermissionReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiPermissionReconciler.class);

    private final AiInterfaceDao interfaceDao;
    private final AiInterfacePermissionRegistrar registrar;
    private final ResourceDao resourceDao;

    @Value("${nway.ai.permission.reconcile:true}")
    private boolean enabled;

    public AiPermissionReconciler(AiInterfaceDao interfaceDao,
                                  AiInterfacePermissionRegistrar registrar,
                                  ResourceDao resourceDao) {
        this.interfaceDao = interfaceDao;
        this.registrar = registrar;
        this.resourceDao = resourceDao;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("AI 接口权限资源对账已关闭（nway.ai.permission.reconcile=false）");
            return;
        }
        try {
            registrar.ensureGroupResource();
            List<AiInterface> interfaces = TenantUtil.callAsSystem(() -> interfaceDao.queryAll());
            Set<String> validCodes = new HashSet<>();
            int synced = 0;
            for (AiInterface entity : interfaces) {
                validCodes.add(AiResourceCodes.of(entity.getInterfaceCode()));
                if (!"PUBLISHED".equals(entity.getStatus())
                        && !AiPermissionService.MODE_RESTRICTED.equals(entity.getAccessMode())) {
                    continue;
                }
                try {
                    registrar.synchronize(entity);
                    synced++;
                } catch (Exception e) {
                    log.warn("AI 接口权限资源同步失败 interfaceCode={}：{}",
                            entity.getInterfaceCode(), e.getMessage());
                }
            }
            int deprecated = deprecateOrphans(validCodes);
            log.info("AI 接口权限资源对账完成：同步 {} 个接口，下线 {} 个无主资源", synced, deprecated);
        } catch (Exception e) {
            // 对账失败不影响启动，运行时判定不依赖资源存在与否
            log.warn("AI 接口权限资源对账失败：{}", e.getMessage(), e);
        }
    }

    private int deprecateOrphans(Set<String> validCodes) {
        int deprecated = 0;
        for (SysResource resource : resourceDao.listByApp(AiPermissionService.APP_CODE)) {
            String code = resource.getResourceCode();
            if (code == null || !code.startsWith("ai:") || AiInterfacePermissionRegistrar.GROUP_RESOURCE_CODE.equals(code)) {
                continue;
            }
            if (validCodes.contains(code) || !SysResource.STATUS_ACTIVE.equals(resource.getStatus())) {
                continue;
            }
            resource.setStatus(SysResource.STATUS_DEPRECATED);
            resource.setModifierId("ai-sync");
            resource.setModifierName("AI 接口");
            resource.setModifiedTime(LocalDateTime.now());
            resourceDao.updateById(resource);
            deprecated++;
        }
        return deprecated;
    }
}
