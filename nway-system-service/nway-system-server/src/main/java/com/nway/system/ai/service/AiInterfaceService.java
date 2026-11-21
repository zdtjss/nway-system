package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.web.model.AiInterfaceVo;
import com.nway.system.ai.domain.AiInvokeConfig;
import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.ai.domain.AiOutputField;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.domain.AiValidationResult;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.tenant.domain.TenantCodePolicy;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.ai.dao.AiInterfaceDao;
import com.nway.system.ai.entity.AiInterface;
import com.nway.system.authorization.dao.GrantDao;
import com.nway.system.authorization.entity.SysGrant;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI 接口元数据服务：CRUD、发布/下线、配置编译与已发布接口缓存。
 * <p>
 * DB 是唯一事实源；运行时只读取已发布配置，不做源码扫描。
 *
 * @author nway
 */
@Service
public class AiInterfaceService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9_]{2,64}$");
    private static final Pattern APP_CODE_PATTERN = Pattern.compile("^[a-z0-9_-]{2,50}$");
    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE");
    private static final Set<String> RISKS = Set.of("READ", "WRITE", "HIGH_RISK");
    private static final Set<String> FIELD_TYPES = Set.of(
            "STRING", "TEXT", "INTEGER", "NUMBER", "BOOLEAN", "DATE", "DATETIME",
            "ENUM", "MULTI_ENUM", "ARRAY", "OBJECT", "FILE", "FILE_ARRAY");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");
    private static final Set<String> ACCESS_MODES = Set.of("PUBLIC", "RESTRICTED");

    /** 测试调用默认超时：人工触发且常面向大模型等慢接口，未显式配置 timeoutMs 时放宽到 120s */
    private static final int TEST_DEFAULT_TIMEOUT_MS = 120_000;

    private final AiInterfaceDao interfaceDao;
    private final AiInterfacePermissionRegistrar permissionRegistrar;
    private final AiInterfacePermissionService permissionService;
    private final AiArgumentValidator argumentValidator;
    private final AiInvoker invoker;
    private final GrantDao grantDao;

    /** 已发布配置缓存：key=租户编码，写操作整体失效（接口数量有限，TTL 兜底） */
    private final com.github.benmanes.caffeine.cache.Cache<String, List<AiInterfaceConfig>> publishedCache =
            Caffeine.newBuilder()
                    .maximumSize(200)
                    .expireAfterWrite(Duration.ofMinutes(1))
                    .build();

    private final com.github.benmanes.caffeine.cache.Cache<String, List<AiInterfaceConfig>> allPublishedCache =
            Caffeine.newBuilder()
                    .maximumSize(1)
                    .expireAfterWrite(Duration.ofMinutes(1))
                    .build();

    public AiInterfaceService(AiInterfaceDao interfaceDao,
                              AiInterfacePermissionRegistrar permissionRegistrar,
                              AiInterfacePermissionService permissionService,
                              AiArgumentValidator argumentValidator,
                              AiInvoker invoker,
                              GrantDao grantDao) {
        this.interfaceDao = interfaceDao;
        this.permissionRegistrar = permissionRegistrar;
        this.permissionService = permissionService;
        this.argumentValidator = argumentValidator;
        this.invoker = invoker;
        this.grantDao = grantDao;
    }

    // ==================== 管理端 ====================

    /**
     * 导出全部配置（符合 ai-interface.schema.json 的 interfaces 数组）。
     */
    public List<AiInterfaceConfig> exportAll() {
        return interfaceDao.queryAll().stream().map(this::toConfig).toList();
    }

    public List<AiInterfaceVo> list(String status, String keyword, String appCode, String tenantCode) {
        List<AiInterface> entities = StringUtils.isBlank(status)
                ? interfaceDao.queryAll() : interfaceDao.queryByStatus(status);
        String kw = StringUtils.trimToEmpty(keyword).toLowerCase(Locale.ROOT);
        String app = StringUtils.trimToEmpty(appCode);
        String tenant = StringUtils.trimToEmpty(tenantCode);
        List<AiInterfaceVo> result = new ArrayList<>(entities.stream()
                .filter(e -> app.isEmpty() || app.equalsIgnoreCase(StringUtils.trimToEmpty(e.getAppCode())))
                .filter(e -> tenant.isEmpty() || tenant.equals(StringUtils.trimToEmpty(e.getTenantCode())))
                .filter(e -> kw.isEmpty()
                        || StringUtils.containsIgnoreCase(e.getInterfaceCode(), kw)
                        || StringUtils.containsIgnoreCase(e.getName(), kw)
                        || StringUtils.containsIgnoreCase(e.getDescription(), kw))
                .map(this::toVo)
                .toList());
        enrichGrantCount(result);
        return result;
    }

    public AiInterfaceVo getById(String id) {
        AiInterface entity = interfaceDao.queryById(id);
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在");
        }
        AiInterfaceVo vo = toVo(entity);
        enrichGrantCount(List.of(vo));
        return vo;
    }

    public AiInterfaceVo getByCodeForAdmin(String interfaceCode) {
        AiInterface entity = TenantUtil.callAsSystem(() -> interfaceDao.queryByCode(interfaceCode));
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在：" + interfaceCode);
        }
        AiInterfaceVo vo = toVo(entity);
        enrichGrantCount(List.of(vo));
        return vo;
    }

    /**
     * 管理端调用测试：按 inputSchema 校验入参后调用目标应用。
     * <p>
     * 与运行时调用保持一致（同样注入 USER_CONTEXT/CONSTANT 字段、透传当前用户 JWT），
     * 但不校验接口级授权，便于发布前验证；草稿/已下线接口同样可测。
     */
    public AiInvokeResult testInvoke(String id, Map<String, Object> args) {
        AiInterface entity = interfaceDao.queryById(id);
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在");
        }
        AiInterfaceConfig config = toConfig(entity);
        AiInvokeConfig invokeConfig = config.getInvokeConfig();
        if (invokeConfig == null) {
            invokeConfig = new AiInvokeConfig();
            config.setInvokeConfig(invokeConfig);
        }
        if (invokeConfig.getTimeoutMs() == null) {
            invokeConfig.setTimeoutMs(TEST_DEFAULT_TIMEOUT_MS);
        }
        AiUserContext user = AiUserContexts.current();
        AiValidationResult validation = argumentValidator.validate(config, args, user);
        if (!validation.isValid()) {
            return AiInvokeResult.failed(config.getInterfaceCode(),
                    validation.getErrorCode(), validation.getMessage());
        }
        return invoker.invoke(config, validation.getValues(), user);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiInterfaceVo save(AiInterfaceConfig config) {
        validate(config);
        String code = config.getInterfaceCode().trim();
        // 编辑必须按主键更新：仅按 interfaceCode 查找会在改编码时插入新记录、旧记录残留
        AiInterface existing = StringUtils.isBlank(config.getId())
                ? null : interfaceDao.queryById(config.getId());
        if (StringUtils.isNotBlank(config.getId()) && existing == null) {
            throw new NwaySystemException("接口配置不存在：" + config.getId());
        }
        if (existing == null) {
            // 新建/导入无 id：按编码幂等覆盖
            existing = TenantUtil.callAsSystem(() -> interfaceDao.queryByCode(code));
        } else if (!code.equals(existing.getInterfaceCode())) {
            AiInterface sameCode = TenantUtil.callAsSystem(() -> interfaceDao.queryByCode(code));
            if (sameCode != null && !sameCode.getId().equals(existing.getId())) {
                throw schemaError("interfaceCode 已被其它接口占用：" + code);
            }
        }
        // 修改记录本体须对记录所属租户有管理权（读共享不等于写授权，public 尤其如此）
        if (existing != null) {
            TenantUtil.assertAdminCanManageTenant(existing.getTenantCode());
        }
        AiInterface entity = existing != null ? existing : new AiInterface();
        String oldCode = existing == null ? null : existing.getInterfaceCode();
        apply(entity, config);
        if (existing == null) {
            entity.setVersion(1);
            entity.setStatus(StringUtils.defaultIfBlank(config.getStatus(), "DRAFT").toUpperCase(Locale.ROOT));
            interfaceDao.insert(entity);
        } else {
            entity.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
            interfaceDao.updateById(entity);
        }
        invalidateCache();
        if (oldCode != null && !oldCode.equals(entity.getInterfaceCode())) {
            // 改名：授权随资源迁移，避免旧编码授权静默失效
            permissionService.rename(oldCode, entity);
        } else if ("PUBLISHED".equals(entity.getStatus())
                || AiPermissionService.MODE_RESTRICTED.equals(entity.getAccessMode())) {
            permissionRegistrar.synchronize(entity);
        }
        AiInterfaceVo vo = toVo(entity);
        enrichGrantCount(List.of(vo));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(String id) {
        AiInterface entity = interfaceDao.queryById(id);
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在");
        }
        TenantUtil.assertAdminCanManageTenant(entity.getTenantCode());
        validate(toConfig(entity));
        if (StringUtils.isBlank(entity.getAccessMode())) {
            entity.setAccessMode("READ".equalsIgnoreCase(entity.getRiskLevel())
                    ? AiPermissionService.MODE_PUBLIC : AiPermissionService.MODE_RESTRICTED);
        }
        entity.setStatus("PUBLISHED");
        // 发布即代表人工审核通过（写接口必须 HUMAN_REVIEWED）
        entity.setMetadataConfidence("HUMAN_REVIEWED");
        entity.setVersion(nextVersion(entity));
        interfaceDao.updateById(entity);
        permissionRegistrar.synchronize(entity);
        invalidateCache();
    }

    @Transactional(rollbackFor = Exception.class)
    public void offline(String id) {
        AiInterface entity = interfaceDao.queryById(id);
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在");
        }
        TenantUtil.assertAdminCanManageTenant(entity.getTenantCode());
        entity.setStatus("OFFLINE");
        entity.setVersion(nextVersion(entity));
        interfaceDao.updateById(entity);
        invalidateCache();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AiInterface entity = interfaceDao.queryById(id);
        if (entity == null) {
            throw new NwaySystemException("接口配置不存在");
        }
        TenantUtil.assertAdminCanManageTenant(entity.getTenantCode());
        permissionService.revoke(entity);
        interfaceDao.deleteById(id);
        invalidateCache();
    }

    // ==================== 运行时 ====================

    /**
     * 查询当前租户可见的已发布接口：本租户 + public 共享 + default 平台级。
     */
    public List<AiInterfaceConfig> getPublishedConfigs(String tenantCode) {
        String key = StringUtils.defaultIfBlank(tenantCode, TenantUtil.DEFAULT_TENANT_CODE);
        return publishedCache.get(key, this::loadPublished);
    }

    public AiInterfaceConfig getPublishedByCode(String tenantCode, String interfaceCode) {
        if (StringUtils.isBlank(interfaceCode)) {
            return null;
        }
        return getPublishedConfigs(tenantCode).stream()
                .filter(c -> interfaceCode.equals(c.getInterfaceCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 跨租户查询已发布接口（工具权限判定用，interfaceCode 全局唯一）。
     */
    public AiInterfaceConfig getAnyPublishedByCode(String interfaceCode) {
        if (StringUtils.isBlank(interfaceCode)) {
            return null;
        }
        List<AiInterfaceConfig> all = allPublishedCache.get("all", key ->
                TenantUtil.callAsSystem(() -> interfaceDao.queryByStatus("PUBLISHED")).stream()
                        .map(this::toConfig).toList());
        return all.stream().filter(c -> interfaceCode.equals(c.getInterfaceCode())).findFirst().orElse(null);
    }

    public void invalidateCache() {
        publishedCache.invalidateAll();
        allPublishedCache.invalidateAll();
    }

    private List<AiInterfaceConfig> loadPublished(String tenantCode) {
        LinkedHashSet<String> tenants = new LinkedHashSet<>();
        tenants.add(tenantCode);
        tenants.add(TenantUtil.PUBLIC_TENANT_CODE);
        tenants.add(TenantUtil.DEFAULT_TENANT_CODE);
        List<AiInterface> entities = TenantUtil.callAsSystem(
                () -> interfaceDao.queryPublishedInTenants(tenants));
        return entities.stream().map(this::toConfig).toList();
    }

    // ==================== 转换 ====================

    public AiInterfaceConfig toConfig(AiInterface entity) {
        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode(entity.getInterfaceCode());
        config.setName(entity.getName());
        config.setDescription(entity.getDescription());
        config.setAppCode(entity.getAppCode());
        config.setHttpMethod(entity.getHttpMethod());
        config.setPath(entity.getPath());
        config.setContentType(entity.getContentType());
        config.setRiskLevel(entity.getRiskLevel());
        config.setConfirmRequired(Boolean.TRUE.equals(entity.getConfirmRequired()));
        config.setInputSchema(parseArray(entity.getInputSchema(), AiFieldDef.class));
        config.setOutputSchema(parseArray(entity.getOutputSchema(), AiOutputField.class));
        config.setInvokeConfig(parseObject(entity.getInvokeConfig(), AiInvokeConfig.class));
        config.setSearchKeywords(entity.getSearchKeywords());
        config.setExamples(parseArray(entity.getExamples(), String.class));
        config.setMetadataConfidence(entity.getMetadataConfidence());
        config.setStatus(entity.getStatus());
        config.setAccessMode(StringUtils.defaultIfBlank(entity.getAccessMode(),
                AiPermissionService.MODE_RESTRICTED));
        config.setVersion(entity.getVersion());
        config.setTenantCode(entity.getTenantCode());
        return config;
    }

    /**
     * 批量填充授权统计：列表页直接展示“已授权 N 个主体/含全部接口授权”。
     */
    private void enrichGrantCount(List<AiInterfaceVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        List<String> resourceCodes = vos.stream()
                .map(vo -> AiResourceCodes.of(vo.getInterfaceCode()))
                .distinct().toList();
        Map<String, Set<String>> subjectsByResource = new HashMap<>();
        for (SysGrant grant : grantDao.queryByResources(AiPermissionService.APP_CODE, resourceCodes)) {
            if (!grant.isAllow()) {
                continue;
            }
            subjectsByResource.computeIfAbsent(grant.getResourceCode(), k -> new LinkedHashSet<>())
                    .add(grant.getSubjectType() + ":" + grant.getSubjectId());
        }
        Set<String> globalSubjects = new LinkedHashSet<>();
        for (SysGrant grant : grantDao.queryByResource(AiPermissionService.APP_CODE,
                AiInterfacePermissionRegistrar.GROUP_RESOURCE_CODE)) {
            if (grant.isAllow()) {
                globalSubjects.add(grant.getSubjectType() + ":" + grant.getSubjectId());
            }
        }
        for (AiInterfaceVo vo : vos) {
            vo.setGrantCount(subjectsByResource
                    .getOrDefault(AiResourceCodes.of(vo.getInterfaceCode()), Set.of()).size());
            vo.setAllGrantCount(globalSubjects.size());
        }
    }

    private AiInterfaceVo toVo(AiInterface entity) {
        AiInterfaceVo vo = ConverterUtil.convert(toConfig(entity), AiInterfaceVo.class);
        vo.setId(entity.getId());
        vo.setCreateTime(entity.getCreateTime());
        vo.setModifiedTime(entity.getModifiedTime());
        if (StringUtils.isBlank(vo.getTenantCode())) {
            vo.setTenantCode(entity.getTenantCode());
        }
        return vo;
    }

    private void apply(AiInterface entity, AiInterfaceConfig config) {
        entity.setInterfaceCode(config.getInterfaceCode().trim());
        entity.setName(config.getName().trim());
        entity.setDescription(StringUtils.defaultString(config.getDescription()).trim());
        entity.setAppCode(config.getAppCode().trim());
        entity.setHttpMethod(config.getHttpMethod().toUpperCase(Locale.ROOT));
        entity.setPath(config.getPath().trim());
        entity.setContentType(StringUtils.defaultIfBlank(config.getContentType(), "application/json"));
        String risk = StringUtils.defaultIfBlank(config.getRiskLevel(), "READ").toUpperCase(Locale.ROOT);
        entity.setRiskLevel(risk);
        // 写操作默认必须确认，避免漏配
        entity.setConfirmRequired(!"READ".equals(risk) || Boolean.TRUE.equals(config.getConfirmRequired()));
        // 访问模式显式化：未指定时保留原值；新建时按风险兜底（只读公开、写操作受控）
        if (StringUtils.isNotBlank(config.getAccessMode())) {
            entity.setAccessMode(config.getAccessMode().trim().toUpperCase(Locale.ROOT));
        } else if (StringUtils.isBlank(entity.getAccessMode())) {
            entity.setAccessMode("READ".equals(risk)
                    ? AiPermissionService.MODE_PUBLIC : AiPermissionService.MODE_RESTRICTED);
        }
        entity.setInputSchema(config.getInputSchema() == null ? "[]" : JSON.toJSONString(config.getInputSchema()));
        entity.setOutputSchema(config.getOutputSchema() == null ? null : JSON.toJSONString(config.getOutputSchema()));
        entity.setInvokeConfig(config.getInvokeConfig() == null ? "{}" : JSON.toJSONString(config.getInvokeConfig()));
        entity.setSearchKeywords(StringUtils.trimToNull(config.getSearchKeywords()));
        entity.setExamples(config.getExamples() == null ? null : JSON.toJSONString(config.getExamples()));
        entity.setMetadataConfidence(StringUtils.defaultIfBlank(config.getMetadataConfidence(), "AUTO"));
        // 导入/显式指定 DRAFT 时允许把已发布接口降回草稿；未指定则保留原状态
        if (StringUtils.isNotBlank(config.getStatus())) {
            entity.setStatus(config.getStatus().toUpperCase(Locale.ROOT));
        }
        entity.setSearchText(buildSearchText(config));
        // 租户可显式指定，但必须落在当前用户可管理范围内（超管不限；管理员限授权租户；
        // 公共租户 public 须显式授权，读共享不等于写授权）。
        // 未传入时由 BaseEntity 填充策略归属当前租户，编辑时保留原租户。
        if (StringUtils.isNotBlank(config.getTenantCode())) {
            String tenantCode = TenantCodePolicy.requireValid(config.getTenantCode().trim());
            TenantUtil.assertAdminCanManageTenant(tenantCode);
            entity.setTenantCode(tenantCode);
        }
    }

    // ==================== 校验 ====================

    public void validate(AiInterfaceConfig config) {
        if (config == null) {
            throw schemaError("配置不能为空");
        }
        String code = StringUtils.trimToEmpty(config.getInterfaceCode());
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw schemaError("interfaceCode 必须为 2~64 位小写字母/数字/下划线");
        }
        requireText(config.getName(), "name", 100);
        if (!APP_CODE_PATTERN.matcher(StringUtils.trimToEmpty(config.getAppCode())).matches()) {
            throw schemaError("appCode 必须为 2~50 位小写字母/数字/中划线/下划线");
        }
        if (!METHODS.contains(StringUtils.upperCase(config.getHttpMethod()))) {
            throw schemaError("httpMethod 仅支持 GET/POST/PUT/DELETE");
        }
        if (StringUtils.isBlank(config.getPath())) {
            throw schemaError("path 不能为空");
        }
        String risk = StringUtils.upperCase(StringUtils.defaultIfBlank(config.getRiskLevel(), "READ"));
        if (!RISKS.contains(risk)) {
            throw schemaError("riskLevel 仅支持 READ/WRITE/HIGH_RISK");
        }
        if (StringUtils.isNotBlank(config.getAccessMode())
                && !ACCESS_MODES.contains(config.getAccessMode().trim().toUpperCase(Locale.ROOT))) {
            throw schemaError("accessMode 仅支持 PUBLIC/RESTRICTED");
        }
        // inputSchema 可空：为空表示支持任意参数，调用时原样透传
        if (config.getInputSchema() != null) {
            Set<String> keys = new LinkedHashSet<>();
            for (AiFieldDef field : config.getInputSchema()) {
                validateField(field, keys, "inputSchema");
            }
        }
        // outputSchema 可省略：省略时原样返回目标响应；path 省略时按 $.{key} 取值
        validateOutputFields(config.getOutputSchema(), "outputSchema");
        // invokeConfig 可选：不配置时按默认 JSON、HTTP 2xx 成功、原样返回处理
        if (StringUtils.isNotBlank(config.getStatus())
                && !STATUSES.contains(config.getStatus().toUpperCase(Locale.ROOT))) {
            throw schemaError("status 仅支持 DRAFT/PUBLISHED/OFFLINE");
        }
    }

    private void validateField(AiFieldDef field, Set<String> keys, String path) {
        if (field == null) {
            throw schemaError(path + " 存在空字段");
        }
        String key = StringUtils.trimToEmpty(field.getKey());
        if (!FIELD_KEY_PATTERN.matcher(key).matches()) {
            throw schemaError(path + " 字段 key 非法：" + key);
        }
        if (!keys.add(key)) {
            throw schemaError(path + " 字段 key 重复：" + key);
        }
        requireText(field.getLabel(), path + "." + key + ".label", 100);
        String type = StringUtils.upperCase(field.getType());
        if (!FIELD_TYPES.contains(type)) {
            throw schemaError(path + "." + key + " type 非法：" + field.getType());
        }
        if ("ENUM".equals(type) || "MULTI_ENUM".equals(type)) {
            boolean hasOptions = field.getOptions() != null && !field.getOptions().isEmpty();
            if (!hasOptions && field.getOptionSource() == null) {
                throw schemaError(path + "." + key + " 为枚举类型，必须配置 options 或 optionSource");
            }
        }
        if (("ARRAY".equals(type) || "FILE_ARRAY".equals(type) || "MULTI_ENUM".equals(type))
                && field.getItems() == null) {
            throw schemaError(path + "." + key + " 为数组类型，必须配置 items");
        }
        if ("OBJECT".equals(type) && (field.getProperties() == null || field.getProperties().isEmpty())) {
            throw schemaError(path + "." + key + " 为对象类型，必须配置 properties");
        }
        if (field.getProperties() != null) {
            Set<String> childKeys = new LinkedHashSet<>();
            for (AiFieldDef child : field.getProperties()) {
                validateField(child, childKeys, path + "." + key);
            }
        }
    }

    /** 出参字段可为扁平定义，也可通过 items/properties 描述数组元素与嵌套对象（仅元数据） */
    private void validateOutputFields(List<AiOutputField> fields, String path) {
        if (fields == null) {
            return;
        }
        for (AiOutputField field : fields) {
            if (field == null || StringUtils.isBlank(field.getKey()) || StringUtils.isBlank(field.getType())) {
                throw schemaError(path + " 字段必须包含 key 与 type");
            }
            validateOutputFields(field.getProperties(), path + "." + field.getKey());
            if (field.getItems() != null) {
                if (StringUtils.isBlank(field.getItems().getType())) {
                    throw schemaError(path + "." + field.getKey() + ".items 必须包含 type");
                }
                validateOutputFields(field.getItems().getProperties(), path + "." + field.getKey() + ".items");
            }
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (StringUtils.isBlank(value)) {
            throw schemaError(field + " 不能为空");
        }
        if (value.length() > maxLength) {
            throw schemaError(field + " 长度不能超过 " + maxLength);
        }
    }

    private NwaySystemException schemaError(String message) {
        return new NwaySystemException(AiErrors.SCHEMA_ERROR + "：" + message);
    }

    private int nextVersion(AiInterface entity) {
        return entity.getVersion() == null ? 1 : entity.getVersion() + 1;
    }

    private String buildSearchText(AiInterfaceConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getName()).append(' ')
                .append(StringUtils.defaultString(config.getDescription())).append(' ')
                .append(StringUtils.defaultString(config.getSearchKeywords())).append(' ');
        if (config.getExamples() != null) {
            config.getExamples().forEach(e -> sb.append(e).append(' '));
        }
        appendFieldText(sb, config.getInputSchema());
        return sb.toString().trim();
    }

    private void appendFieldText(StringBuilder sb, List<AiFieldDef> fields) {
        if (fields == null) {
            return;
        }
        for (AiFieldDef field : fields) {
            sb.append(field.getLabel()).append(' ');
            if (StringUtils.isNotBlank(field.getDescription())) {
                sb.append(field.getDescription()).append(' ');
            }
            if (field.getOptions() != null) {
                field.getOptions().forEach(o -> sb.append(o.getLabel()).append(' '));
            }
            appendFieldText(sb, field.getProperties());
        }
    }

    private <T> List<T> parseArray(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(json, clazz);
        } catch (Exception e) {
            throw schemaError("JSON 解析失败：" + e.getMessage());
        }
    }

    private <T> T parseObject(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            throw schemaError("JSON 解析失败：" + e.getMessage());
        }
    }
}
