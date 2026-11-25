package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.ai.web.model.AiChatModelVo;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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

    /** 未配置时的单次输出上限：过小会让长回答在模型侧被静默截断 */
    public static final int DEFAULT_MAX_TOKENS = 8192;

    private static final BigDecimal MIN_PENALTY = new BigDecimal("-2");
    private static final BigDecimal MAX_PENALTY = new BigDecimal("2");

    /** 常见多模态模型名特征：配置未显式指定视觉能力时用于自动识别 */
    private static final List<Pattern> VISION_MODEL_PATTERNS = List.of(
            Pattern.compile("gpt-(4o|4\\.\\d|4-turbo|4-vision|5)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[/:])o[134](?!-mini)(-|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("claude-(3|4|sonnet|opus|haiku)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("gemini", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(qwen|qvq)[^\\s,]*(vl|omni|vision)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("glm-4[\\d.]*v", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(llava|internvl|pixtral|minicpm-v|cogvlm|yi-vl|ovis|molmo|janus)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("step-\\d+[vo](-|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ernie[^\\s,]*(vl|vision)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("deepseek[^\\s,]*(vl|ocr)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(moonshot[^\\s,]*vision|kimi-latest|kimi[^\\s,]*vl)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vision", Pattern.CASE_INSENSITIVE)
    );

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
        validateGenerationParams(request);
        String extraBody = normalizeExtraBody(request.getExtraBody());
        AiModel entity;
        if (StringUtils.isBlank(request.getId())) {
            entity = new AiModel();
            if (StringUtils.isNotBlank(request.getApiKey())) {
                entity.setApiKeyCipher(crypto.encrypt(request.getApiKey().trim()));
            }
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
        entity.setSupportsVision(request.getSupportsVision());
        entity.setContextWindowTokens(request.getContextWindowTokens());
        entity.setTopP(request.getTopP());
        entity.setFrequencyPenalty(request.getFrequencyPenalty());
        entity.setPresencePenalty(request.getPresencePenalty());
        entity.setReasoningEffort(normalizeReasoningEffort(request.getReasoningEffort()));
        entity.setThinkingEnabled(request.getThinkingEnabled());
        entity.setThinkingBudget(request.getThinkingBudget());
        entity.setExtraBody(extraBody);
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
        AiModel model = defaultCache.get(key, k -> TenantUtil.callAsSystem(
                () -> modelDao.queryDefaultInTenants(readableTenants(k))));
        if (model == null) {
            throw new NwaySystemException(AiErrors.MODEL_ERROR + "：未配置默认模型，请在「AI 接口配置 - 模型设置」中配置");
        }
        return model;
    }

    /**
     * 对话页可选模型：当前用户可用租户内启用中的模型。
     */
    public List<AiChatModelVo> listChatModels(String tenantCode) {
        java.util.Set<String> tenants = readableTenants(tenantCode);
        return TenantUtil.callAsSystem(() -> modelDao.queryAll().stream()
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .filter(item -> tenants.contains(StringUtils.defaultString(item.getTenantCode())))
                .map(this::toChatVo)
                .toList());
    }

    /**
     * 解析用户选择的对话模型；未选择时使用默认模型。
     * 选择结果必须启用且在当前用户可用租户内，避免跨租户使用或使用已停用配置。
     */
    public AiModel getChatModel(String tenantCode, String modelId) {
        if (StringUtils.isBlank(modelId)) {
            return getDefaultModel(tenantCode);
        }
        AiModel model = TenantUtil.callAsSystem(() -> modelDao.queryById(modelId));
        if (model == null || !"ACTIVE".equals(model.getStatus())
                || !readableTenants(tenantCode).contains(StringUtils.defaultString(model.getTenantCode()))) {
            throw new NwaySystemException(AiErrors.MODEL_ERROR + "：所选模型不可用，请刷新页面后重新选择");
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
        GenerateOptions.Builder optionsBuilder = GenerateOptions.builder()
                .temperature(entity.getTemperature() == null ? 0.3 : entity.getTemperature().doubleValue())
                .maxTokens(effectiveMaxTokens(entity));
        if (entity.getTopP() != null) {
            optionsBuilder.topP(entity.getTopP().doubleValue());
        }
        if (entity.getFrequencyPenalty() != null) {
            optionsBuilder.frequencyPenalty(entity.getFrequencyPenalty().doubleValue());
        }
        if (entity.getPresencePenalty() != null) {
            optionsBuilder.presencePenalty(entity.getPresencePenalty().doubleValue());
        }
        // 关闭思考时不发送推理深度与思考预算；模型默认开启思考时可在额外参数中按厂商要求关闭
        if (!Boolean.FALSE.equals(entity.getThinkingEnabled())) {
            if (StringUtils.isNotBlank(entity.getReasoningEffort())) {
                optionsBuilder.reasoningEffort(entity.getReasoningEffort().trim());
            }
            if (entity.getThinkingBudget() != null) {
                optionsBuilder.thinkingBudget(entity.getThinkingBudget());
            }
        }
        parseExtraBody(entity.getExtraBody()).forEach(optionsBuilder::additionalBodyParam);
        GenerateOptions options = optionsBuilder.build();

        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(entity.getModelName())
                .baseUrl(entity.getBaseUrl())
                .generateOptions(options)
                .stream(true);
        if (entity.getContextWindowTokens() != null && entity.getContextWindowTokens() > 0) {
            modelBuilder.contextWindowSize(entity.getContextWindowTokens());
        }
        log.debug("构建大模型客户端 name={} modelName={} baseUrl={} temperature={} maxTokens={} topP={} "
                        + "frequencyPenalty={} presencePenalty={} reasoningEffort={} thinkingEnabled={} "
                        + "thinkingBudget={} contextWindowTokens={} vision={}",
                entity.getName(), entity.getModelName(), entity.getBaseUrl(),
                options.getTemperature(), options.getMaxTokens(), options.getTopP(),
                options.getFrequencyPenalty(), options.getPresencePenalty(), options.getReasoningEffort(),
                entity.getThinkingEnabled(), options.getThinkingBudget(), entity.getContextWindowTokens(),
                resolveVisionSupported(entity));
        return modelBuilder.build();
    }

    /** 实际生效的单次输出上限，未配置时使用默认值。 */
    public int effectiveMaxTokens(AiModel entity) {
        return entity == null || entity.getMaxTokens() == null
                ? DEFAULT_MAX_TOKENS : entity.getMaxTokens();
    }

    /** 视觉能力：显式配置优先，未配置时按模型名自动识别。 */
    public boolean resolveVisionSupported(AiModel entity) {
        if (entity.getSupportsVision() != null) {
            return entity.getSupportsVision();
        }
        return guessVisionByModelName(entity.getModelName());
    }

    static boolean guessVisionByModelName(String modelName) {
        String name = StringUtils.trimToEmpty(modelName);
        if (name.isEmpty()) {
            return false;
        }
        return VISION_MODEL_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(name).find());
    }

    public void invalidate() {
        defaultCache.invalidateAll();
    }

    private void validateGenerationParams(AiModelSaveRequest request) {
        if (request.getTopP() != null
                && (request.getTopP().compareTo(BigDecimal.ZERO) < 0
                || request.getTopP().compareTo(BigDecimal.ONE) > 0)) {
            throw new NwaySystemException("topP 需在 0 ~ 1 之间");
        }
        validatePenalty(request.getFrequencyPenalty(), "频率惩罚");
        validatePenalty(request.getPresencePenalty(), "存在惩罚");
        if (request.getContextWindowTokens() != null && request.getContextWindowTokens() <= 0) {
            throw new NwaySystemException("最大上下文需大于 0");
        }
        if (request.getThinkingBudget() != null && request.getThinkingBudget() < 0) {
            throw new NwaySystemException("思考预算不能为负数");
        }
    }

    private void validatePenalty(BigDecimal value, String label) {
        if (value != null && (value.compareTo(MIN_PENALTY) < 0 || value.compareTo(MAX_PENALTY) > 0)) {
            throw new NwaySystemException(label + "需在 -2 ~ 2 之间");
        }
    }

    private String normalizeReasoningEffort(String value) {
        String effort = StringUtils.trimToNull(value);
        if (effort == null) {
            return null;
        }
        if (effort.length() > 20) {
            throw new NwaySystemException("推理深度最长 20 个字符");
        }
        return effort.toLowerCase(Locale.ROOT);
    }

    private String normalizeExtraBody(String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            return null;
        }
        parseExtraBody(text);
        return text;
    }

    /** 额外请求参数：仅接受 JSON 对象，用于透传厂商专有字段。 */
    private Map<String, Object> parseExtraBody(String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            return Map.of();
        }
        Object parsed;
        try {
            parsed = JSON.parse(text);
        } catch (Exception e) {
            throw new NwaySystemException("额外请求参数不是合法 JSON：" + e.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new NwaySystemException("额外请求参数必须是 JSON 对象");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        map.forEach((key, item) -> params.put(String.valueOf(key), item));
        return params;
    }

    /** 用户可读模型租户：本租户 + public 共享 + default 兜底。 */
    private java.util.Set<String> readableTenants(String tenantCode) {
        String tenant = StringUtils.defaultIfBlank(tenantCode, TenantUtil.DEFAULT_TENANT_CODE);
        LinkedHashSet<String> tenants = new LinkedHashSet<>();
        tenants.add(tenant);
        tenants.add(TenantUtil.PUBLIC_TENANT_CODE);
        tenants.add(TenantUtil.DEFAULT_TENANT_CODE);
        return tenants;
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
        vo.setSupportsVision(entity.getSupportsVision());
        vo.setVisionSupported(resolveVisionSupported(entity));
        vo.setContextWindowTokens(entity.getContextWindowTokens());
        vo.setTopP(entity.getTopP());
        vo.setFrequencyPenalty(entity.getFrequencyPenalty());
        vo.setPresencePenalty(entity.getPresencePenalty());
        vo.setReasoningEffort(entity.getReasoningEffort());
        vo.setThinkingEnabled(entity.getThinkingEnabled());
        vo.setThinkingBudget(entity.getThinkingBudget());
        vo.setExtraBody(entity.getExtraBody());
        vo.setIsDefault(entity.getIsDefault());
        vo.setStatus(entity.getStatus());
        vo.setApiKeyConfigured(StringUtils.isNotBlank(entity.getApiKeyCipher()));
        vo.setModifiedTime(entity.getModifiedTime());
        return vo;
    }

    private AiChatModelVo toChatVo(AiModel entity) {
        AiChatModelVo vo = new AiChatModelVo();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setModelName(entity.getModelName());
        vo.setIsDefault(entity.getIsDefault());
        vo.setSupportsVision(resolveVisionSupported(entity));
        boolean reasoningConfigured = StringUtils.isNotBlank(entity.getReasoningEffort())
                || entity.getThinkingBudget() != null;
        vo.setThinkingEnabled(Boolean.TRUE.equals(entity.getThinkingEnabled())
                || (entity.getThinkingEnabled() == null && reasoningConfigured));
        vo.setReasoningEffort(Boolean.FALSE.equals(entity.getThinkingEnabled())
                ? null : StringUtils.trimToNull(entity.getReasoningEffort()));
        vo.setContextWindowTokens(entity.getContextWindowTokens());
        return vo;
    }
}
