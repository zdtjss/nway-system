package com.nway.system.ai.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.metadata.service.DictService;
import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.metadata.web.model.DictItemDto;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.tenant.TenantUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 选项数据源解析：静态 / 字典 / HTTP / 搜索。
 * <p>
 * 失败统一返回 OPTION_SOURCE_UNAVAILABLE，不猜测选项值。
 *
 * @author nway
 */
@Service
public class AiOptionService {

    private final DictService dictService;
    private final AiInvoker aiInvoker;

    private final com.github.benmanes.caffeine.cache.Cache<String, List<AiFieldDef.AiOption>> optionCache =
            Caffeine.newBuilder().maximumSize(2_000).expireAfterWrite(Duration.ofMinutes(5)).build();

    public AiOptionService(DictService dictService, AiInvoker aiInvoker) {
        this.dictService = dictService;
        this.aiInvoker = aiInvoker;
    }

    /**
     * 解析字段的全部候选选项。
     */
    public List<AiFieldDef.AiOption> resolve(AiFieldDef field, String keyword, AiUserContext user) {
        if (field.getOptions() != null && !field.getOptions().isEmpty()) {
            return filter(field.getOptions(), keyword);
        }
        AiFieldDef.AiOptionSource source = field.getOptionSource();
        if (source == null) {
            return List.of();
        }
        String type = StringUtils.upperCase(StringUtils.defaultIfBlank(source.getType(), "DICT"));
        return switch (type) {
            case "DICT" -> filter(asTenant(user, () -> dictOptions(source, user)), keyword);
            case "HTTP", "SEARCH" -> httpOptions(source, keyword, user);
            default -> throw new NwaySystemException(AiErrors.OPTION_SOURCE_UNAVAILABLE + "：不支持的选项源 " + type);
        };
    }

    private List<AiFieldDef.AiOption> dictOptions(AiFieldDef.AiOptionSource source, AiUserContext user) {
        String tenant = user == null ? "" : StringUtils.defaultString(user.effectiveTenantCode());
        String cacheKey = "dict:" + tenant + ":" + source.getDictCode();
        return optionCache.get(cacheKey, key -> {
            List<DictItemDto> items = dictService.queryByCode(source.getDictCode());
            String valueField = StringUtils.defaultIfBlank(source.getValueField(), "key");
            String labelField = StringUtils.defaultIfBlank(source.getLabelField(), "value");
            List<AiFieldDef.AiOption> options = new ArrayList<>();
            for (DictItemDto item : items) {
                Map<String, Object> map = item.getItemValue();
                if (map == null) {
                    continue;
                }
                AiFieldDef.AiOption option = new AiFieldDef.AiOption();
                option.setValue(map.get(valueField));
                option.setLabel(String.valueOf(map.getOrDefault(labelField, map.get(valueField))));
                if (option.getValue() != null) {
                    options.add(option);
                }
            }
            return options;
        });
    }

    private List<AiFieldDef.AiOption> httpOptions(AiFieldDef.AiOptionSource source, String keyword, AiUserContext user) {
        String tenant = user == null ? "" : StringUtils.defaultString(user.effectiveTenantCode());
        String cacheKey = "http:" + tenant + ":" + source.getAppCode() + ":" + source.getPath()
                + ":" + StringUtils.defaultString(keyword);
        int ttl = source.getCacheTtlSeconds() == null ? 300 : Math.max(0, source.getCacheTtlSeconds());
        if (ttl == 0) {
            return loadHttpOptions(source, keyword, user);
        }
        long now = System.currentTimeMillis();
        HttpCacheEntry entry = httpCache.get(cacheKey);
        if (entry != null && entry.expireAt > now) {
            return entry.options;
        }
        List<AiFieldDef.AiOption> options = loadHttpOptions(source, keyword, user);
        httpCache.put(cacheKey, new HttpCacheEntry(options, now + ttl * 1000L));
        return options;
    }

    private record HttpCacheEntry(List<AiFieldDef.AiOption> options, long expireAt) {
    }

    private final java.util.concurrent.ConcurrentHashMap<String, HttpCacheEntry> httpCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private List<AiFieldDef.AiOption> loadHttpOptions(AiFieldDef.AiOptionSource source, String keyword,
                                                     AiUserContext user) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(keyword) && StringUtils.isNotBlank(source.getKeywordParam())) {
            body.put(source.getKeywordParam(), keyword);
        }
        Object response = aiInvoker.fetchOptions(source.getAppCode(),
                StringUtils.defaultIfBlank(source.getMethod(), "POST"),
                source.getPath(), body,
                user == null ? null : user.getToken(),
                user == null ? null : user.effectiveTenantCode(),
                source.getCacheTtlSeconds() == null ? 10_000 : Math.max(1000, source.getCacheTtlSeconds() * 1000));
        List<Object> rows = extractRows(response);
        String valueField = StringUtils.defaultIfBlank(source.getValueField(), "value");
        String labelField = StringUtils.defaultIfBlank(source.getLabelField(), "label");
        List<AiFieldDef.AiOption> options = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                Object value = map.get(valueField);
                Object label = map.get(labelField);
                if (value == null) {
                    continue;
                }
                AiFieldDef.AiOption option = new AiFieldDef.AiOption();
                option.setValue(value);
                option.setLabel(label == null ? String.valueOf(value) : String.valueOf(label));
                options.add(option);
            }
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractRows(Object response) {
        if (response instanceof List<?> list) {
            return (List<Object>) list;
        }
        if (response instanceof Map<?, ?> map) {
            for (String key : List.of("data", "rows", "records", "list", "items", "result")) {
                Object value = map.get(key);
                if (value instanceof List<?> list) {
                    return (List<Object>) list;
                }
                if (value instanceof Map<?, ?> nested) {
                    List<Object> deeper = extractRows(nested);
                    if (!deeper.isEmpty()) {
                        return deeper;
                    }
                }
            }
        }
        throw new NwaySystemException(AiErrors.OPTION_SOURCE_UNAVAILABLE + "：选项响应结构无法识别");
    }

    private List<AiFieldDef.AiOption> filter(List<AiFieldDef.AiOption> options, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return options;
        }
        String kw = keyword.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> StringUtils.containsIgnoreCase(o.getLabel(), kw)
                        || StringUtils.containsIgnoreCase(String.valueOf(o.getValue()), kw))
                .toList();
    }

    private <T> T asTenant(AiUserContext user, Supplier<T> supplier) {
        String tenant = user == null ? null : user.getTenantCode();
        if (StringUtils.isBlank(tenant)) {
            return supplier.get();
        }
        Object[] holder = new Object[1];
        TenantUtil.runAsTenant(tenant, () -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T result = (T) holder[0];
        return result;
    }
}
