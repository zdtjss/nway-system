package com.nway.system.ai.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.ai.web.model.AiModelSaveRequest;
import com.nway.system.ai.web.model.AiModelVo;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.ai.dao.AiModelDao;
import com.nway.system.ai.entity.AiModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 模型 Provider 服务：配置管理与运行时模型构建。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiModelService {

    private final AiModelDao modelDao;
    private final AiCrypto crypto;

    private final com.github.benmanes.caffeine.cache.Cache<String, AiModel> defaultCache =
            Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofMinutes(1)).build();

    public AiModelService(AiModelDao modelDao, AiCrypto crypto) {
        this.modelDao = modelDao;
        this.crypto = crypto;
    }

    public List<AiModelVo> list() {
        return modelDao.queryAll().stream().map(this::toVo).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AiModelVo save(AiModelSaveRequest request) {
        if (StringUtils.isBlank(request.getName())) {
            throw new NwaySystemException("配置名不能为空");
        }
        if (StringUtils.isBlank(request.getBaseUrl())) {
            throw new NwaySystemException("baseUrl 不能为空");
        }
        if (StringUtils.isBlank(request.getModelName())) {
            throw new NwaySystemException("模型名不能为空");
        }
        AiModel entity;
        if (StringUtils.isBlank(request.getId())) {
            if (StringUtils.isBlank(request.getApiKey())) {
                throw new NwaySystemException("API Key 不能为空");
            }
            entity = new AiModel();
            entity.setApiKeyCipher(crypto.encrypt(request.getApiKey().trim()));
            entity.setStatus(StringUtils.defaultIfBlank(request.getStatus(), "ACTIVE"));
        } else {
            entity = modelDao.queryById(request.getId());
            if (entity == null) {
                throw new NwaySystemException("模型配置不存在");
            }
            if (StringUtils.isNotBlank(request.getApiKey())) {
                entity.setApiKeyCipher(crypto.encrypt(request.getApiKey().trim()));
            }
        }
        entity.setName(request.getName().trim());
        entity.setBaseUrl(request.getBaseUrl().trim());
        entity.setModelName(request.getModelName().trim());
        entity.setTemperature(request.getTemperature());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setSupportsToolCalling(!Boolean.FALSE.equals(request.getSupportsToolCalling()));
        entity.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        entity.setStatus(StringUtils.defaultIfBlank(request.getStatus(), "ACTIVE"));

        if (StringUtils.isBlank(request.getId())) {
            modelDao.insert(entity);
        } else {
            modelDao.updateById(entity);
        }
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearOtherDefaults(entity);
        }
        invalidate();
        return toVo(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (modelDao.queryById(id) == null) {
            throw new NwaySystemException("模型配置不存在");
        }
        modelDao.deleteById(id);
        invalidate();
    }

    /**
     * 取当前用户可用默认模型：本租户 + public + default。
     */
    public AiModel getDefaultModel(String tenantCode) {
        String key = StringUtils.defaultIfBlank(tenantCode, TenantUtil.DEFAULT_TENANT_CODE);
        AiModel model = defaultCache.get(key, k -> {
            LinkedHashSet<String> tenants = new LinkedHashSet<>();
            tenants.add(k);
            tenants.add(TenantUtil.PUBLIC_TENANT_CODE);
            tenants.add(TenantUtil.DEFAULT_TENANT_CODE);
            return TenantUtil.callAsSystem(() -> modelDao.queryDefaultInTenants(tenants));
        });
        if (model == null) {
            throw new NwaySystemException(AiErrors.MODEL_ERROR + "：未配置默认模型，请在「AI 接口配置 - 模型设置」中配置");
        }
        return model;
    }

    /**
     * 取语义路由使用的模型：配置了 routing-model 时按名称匹配，否则回退默认模型。
     * 按名称查找时仍限制在当前用户可用租户内，避免跨租户使用模型。
     */
    public AiModel getRoutingModel(String tenantCode, String routingModelName) {
        if (StringUtils.isBlank(routingModelName)) {
            return getDefaultModel(tenantCode);
        }
        String tenant = StringUtils.defaultIfBlank(tenantCode, TenantUtil.DEFAULT_TENANT_CODE);
        java.util.Set<String> allowedTenants = java.util.Set.of(
                tenant, TenantUtil.PUBLIC_TENANT_CODE, TenantUtil.DEFAULT_TENANT_CODE);
        String target = routingModelName.trim();
        AiModel model = TenantUtil.callAsSystem(() -> modelDao.queryAll().stream()
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .filter(item -> allowedTenants.contains(StringUtils.defaultString(item.getTenantCode())))
                .filter(item -> target.equalsIgnoreCase(StringUtils.trimToEmpty(item.getName()))
                        || target.equalsIgnoreCase(StringUtils.trimToEmpty(item.getModelName())))
                .findFirst()
                .orElse(null));
        if (model == null) {
            log.warn("未找到语义路由模型 name={}，回退默认模型", target);
            return getDefaultModel(tenantCode);
        }
        return model;
    }

    /**
     * 构建 OpenAI 兼容模型客户端。
     */
    public Model buildModel(AiModel entity) {
        String apiKey = crypto.decrypt(entity.getApiKeyCipher());
        GenerateOptions options = GenerateOptions.builder()
                .temperature(entity.getTemperature() == null ? 0.3 : entity.getTemperature().doubleValue())
                .maxTokens(entity.getMaxTokens() == null ? 4096 : entity.getMaxTokens())
                .build();
        log.debug("构建大模型客户端 name={} modelName={} baseUrl={} temperature={} maxTokens={}",
                entity.getName(), entity.getModelName(), entity.getBaseUrl(),
                options.getTemperature(), options.getMaxTokens());
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(entity.getModelName())
                .baseUrl(entity.getBaseUrl())
                .generateOptions(options)
                .stream(true)
                .build();
    }

    public void invalidate() {
        defaultCache.invalidateAll();
    }

    private void clearOtherDefaults(AiModel current) {
        for (AiModel model : modelDao.queryAll()) {
            if (!model.getId().equals(current.getId()) && Boolean.TRUE.equals(model.getIsDefault())) {
                model.setIsDefault(false);
                modelDao.updateById(model);
            }
        }
    }

    private AiModelVo toVo(AiModel entity) {
        AiModelVo vo = new AiModelVo();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setModelName(entity.getModelName());
        vo.setTemperature(entity.getTemperature());
        vo.setMaxTokens(entity.getMaxTokens());
        vo.setSupportsToolCalling(entity.getSupportsToolCalling());
        vo.setIsDefault(entity.getIsDefault());
        vo.setStatus(entity.getStatus());
        vo.setApiKeyConfigured(StringUtils.isNotBlank(entity.getApiKeyCipher()));
        vo.setModifiedTime(entity.getModifiedTime());
        return vo;
    }
}
