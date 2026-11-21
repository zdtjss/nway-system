package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.domain.AiValidationResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 参数校验与上下文注入。
 * <p>
 * 顺序：必填 → 类型 → 选项 → 范围/正则 → 非 LLM 字段注入。
 * 失败返回规范错误码，由工具转成模型可读的 JSON。
 *
 * @author nway
 */
@Component
public class AiArgumentValidator {

    private static final DateTimeFormatter DATETIME_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));
    private static final List<DateTimeFormatter> DATETIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    private final AiOptionService optionService;

    public AiArgumentValidator(AiOptionService optionService) {
        this.optionService = optionService;
    }

    public AiValidationResult validate(AiInterfaceConfig config, Map<String, Object> rawArgs, AiUserContext user) {
        Map<String, Object> args = rawArgs == null ? Map.of() : rawArgs;
        List<AiFieldDef> schema = config.getInputSchema() == null ? List.of() : config.getInputSchema();
        if (schema.isEmpty()) {
            // 未声明入参：支持任意参数，原样透传
            return AiValidationResult.ok(new LinkedHashMap<>(args));
        }

        Set<String> allowedKeys = new LinkedHashSet<>();
        schema.stream().filter(AiFieldDef::isLlmVisible).forEach(f -> allowedKeys.add(f.getKey()));
        List<String> unknown = args.keySet().stream().filter(k -> !allowedKeys.contains(k)).toList();
        if (!unknown.isEmpty()) {
            String allowed = allowedKeys.isEmpty() ? "无" : String.join(", ", allowedKeys);
            return AiValidationResult.fail(AiErrors.INVALID_ARGUMENT,
                    "存在未定义的参数：" + String.join(", ", unknown) + "；允许参数：" + allowed);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (AiFieldDef field : schema) {
            if (field.isLlmVisible()) {
                Object value = args.get(field.getKey());
                if (value == null) {
                    value = field.getDefaultValue();
                }
                if (value == null) {
                    if (Boolean.TRUE.equals(field.getRequired())) {
                        return AiValidationResult.fail(AiErrors.INVALID_ARGUMENT,
                                "缺少必填字段：" + field.getLabel() + "（" + field.getKey() + "）");
                    }
                    continue;
                }
                Object normalized = normalize(field, value, user);
                if (normalized instanceof AiValidationResult failed) {
                    return failed;
                }
                values.put(field.getKey(), normalized);
            } else {
                Object value = inject(field, user);
                if (value == null) {
                    if (Boolean.TRUE.equals(field.getRequired())) {
                        return AiValidationResult.fail(AiErrors.USER_CONTEXT_MISSING,
                                "无法注入字段：" + field.getLabel() + "（" + field.getKey() + "）");
                    }
                    continue;
                }
                values.put(field.getKey(), value);
            }
        }
        return AiValidationResult.ok(values);
    }

    private Object normalize(AiFieldDef field, Object value, AiUserContext user) {
        String type = StringUtils.upperCase(StringUtils.defaultIfBlank(field.getType(), "STRING"));
        Object normalized;
        try {
            normalized = switch (type) {
                case "INTEGER" -> toLong(value);
                case "NUMBER" -> toDecimal(value);
                case "BOOLEAN" -> toBoolean(value);
                case "DATE" -> toDate(value);
                case "DATETIME" -> toDateTime(value);
                case "ENUM" -> resolveEnum(field, value, user);
                case "MULTI_ENUM" -> resolveMultiEnum(field, value, user);
                case "ARRAY" -> toList(value, field.getKey());
                case "FILE_ARRAY" -> toStringList(value, field.getKey());
                case "OBJECT" -> toObject(field, value, user);
                case "FILE" -> String.valueOf(value);
                default -> value instanceof String s ? s : String.valueOf(value);
            };
        } catch (AiValidationResultException e) {
            return AiValidationResult.fail(AiErrors.INVALID_ARGUMENT, e.getMessage());
        }
        if (normalized instanceof AiValidationResult) {
            return normalized;
        }
        String rangeError = checkValidation(field, normalized);
        if (rangeError != null) {
            return AiValidationResult.fail(AiErrors.INVALID_ARGUMENT, rangeError);
        }
        return normalized;
    }

    private Object resolveEnum(AiFieldDef field, Object value, AiUserContext user) {
        List<AiFieldDef.AiOption> options = optionService.resolve(field, null, user);
        if (options == null || options.isEmpty()) {
            return AiValidationResult.fail(AiErrors.OPTION_SOURCE_UNAVAILABLE,
                    "字段 " + field.getLabel() + " 的选项源不可用");
        }
        List<AiFieldDef.AiOption> byValue = options.stream()
                .filter(o -> String.valueOf(o.getValue()).equals(String.valueOf(value)))
                .toList();
        if (byValue.size() == 1) {
            return byValue.get(0).getValue();
        }
        List<AiFieldDef.AiOption> byLabel = options.stream()
                .filter(o -> Strings.CI.equals(o.getLabel(), String.valueOf(value)))
                .toList();
        if (byLabel.size() == 1) {
            return byLabel.get(0).getValue();
        }
        if (byLabel.size() > 1) {
            return AiValidationResult.fail(AiErrors.INVALID_OPTION,
                    "字段 " + field.getLabel() + " 的取值有歧义：" + String.valueOf(value)
                            + "，候选：" + byLabel.stream().map(AiFieldDef.AiOption::getLabel).toList());
        }
        return AiValidationResult.fail(AiErrors.INVALID_OPTION,
                "字段 " + field.getLabel() + " 不支持取值：" + String.valueOf(value)
                        + "，可选：" + options.stream().map(AiFieldDef.AiOption::getLabel).limit(30).toList());
    }

    private Object resolveMultiEnum(AiFieldDef field, Object value, AiUserContext user) {
        List<Object> list = toList(value, field.getKey());
        List<Object> resolved = new ArrayList<>();
        for (Object item : list) {
            Object one = resolveEnum(field, item, user);
            if (one instanceof AiValidationResult failed) {
                return failed;
            }
            resolved.add(one);
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Object toObject(AiFieldDef field, Object value, AiUserContext user) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new AiValidationResultException("字段 " + field.getLabel() + " 必须为对象");
        }
        if (field.getProperties() == null) {
            return value;
        }
        Map<String, Object> source = (Map<String, Object>) map;
        Map<String, Object> result = new LinkedHashMap<>();
        for (AiFieldDef child : field.getProperties()) {
            Object childValue = source.get(child.getKey());
            if (childValue == null) {
                childValue = child.getDefaultValue();
            }
            if (childValue == null) {
                if (Boolean.TRUE.equals(child.getRequired())) {
                    return AiValidationResult.fail(AiErrors.INVALID_ARGUMENT,
                            "缺少必填字段：" + field.getLabel() + "." + child.getLabel());
                }
                continue;
            }
            Object normalized = normalize(child, childValue, user);
            if (normalized instanceof AiValidationResult failed) {
                return failed;
            }
            result.put(child.getKey(), normalized);
        }
        return result;
    }

    private String checkValidation(AiFieldDef field, Object value) {
        Map<String, Object> rules = field.getValidation();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString());
            if (rules.get("min") instanceof Number min && decimal.compareTo(new BigDecimal(min.toString())) < 0) {
                return field.getLabel() + " 不能小于 " + min;
            }
            if (rules.get("max") instanceof Number max && decimal.compareTo(new BigDecimal(max.toString())) > 0) {
                return field.getLabel() + " 不能大于 " + max;
            }
        }
        if (value instanceof String s) {
            if (rules.get("minLength") instanceof Number min && s.length() < min.intValue()) {
                return field.getLabel() + " 长度不能小于 " + min;
            }
            if (rules.get("maxLength") instanceof Number max && s.length() > max.intValue()) {
                return field.getLabel() + " 长度不能超过 " + max;
            }
            Object pattern = rules.get("pattern");
            if (pattern != null && !Pattern.compile(String.valueOf(pattern)).matcher(s).matches()) {
                return field.getLabel() + " 格式不合法";
            }
        }
        if (value instanceof Collection<?> list) {
            if (rules.get("minItems") instanceof Number min && list.size() < min.intValue()) {
                return field.getLabel() + " 至少需要 " + min + " 项";
            }
            if (rules.get("maxItems") instanceof Number max && list.size() > max.intValue()) {
                return field.getLabel() + " 最多 " + max + " 项";
            }
        }
        return null;
    }

    private Object inject(AiFieldDef field, AiUserContext user) {
        String source = StringUtils.upperCase(StringUtils.defaultIfBlank(field.getValueSource(), "LLM"));
        if ("CONSTANT".equals(source)) {
            String expression = field.getValueExpression();
            return expression != null ? expression : field.getDefaultValue();
        }
        if ("TOOL_CONTEXT".equals(source)) {
            return user == null ? null : user.getConversationId();
        }
        if ("USER_CONTEXT".equals(source)) {
            if (user == null) {
                return null;
            }
            String expression = StringUtils.defaultIfBlank(field.getValueExpression(), "${user.employeeId}");
            return switch (expression.trim()) {
                case "${user.employeeId}", "${user.userId}" -> user.getEmployeeId();
                case "${user.realName}", "${user.name}" -> user.getEmployeeName();
                case "${user.tenantCode}" -> user.getTenantCode();
                case "${user.token}" -> user.getToken();
                case "${user.deptCode}" -> user.getDeptCodes() == null || user.getDeptCodes().isEmpty()
                        ? null : user.getDeptCodes().get(0);
                case "${user.roles}" -> user.getRoleCodes();
                default -> expression;
            };
        }
        return field.getDefaultValue();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new AiValidationResultException("期望整数，实际为：" + value);
        }
    }

    private BigDecimal toDecimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new AiValidationResultException("期望数字，实际为：" + value);
        }
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "1", "y", "yes" -> true;
            case "false", "0", "n", "no" -> false;
            default -> throw new AiValidationResultException("期望布尔值，实际为：" + value);
        };
    }

    private String toDate(Object value) {
        String s = String.valueOf(value).trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, formatter).toString();
            } catch (Exception ignore) {
                // try next
            }
        }
        throw new AiValidationResultException("日期格式应为 yyyy-MM-dd，实际为：" + value);
    }

    private String toDateTime(Object value) {
        String s = String.valueOf(value).trim().replace(' ', 'T');
        for (DateTimeFormatter formatter : DATETIME_FORMATS) {
            try {
                return LocalDateTime.parse(s, formatter).format(DATETIME_OUT);
            } catch (Exception ignore) {
                // try next
            }
        }
        throw new AiValidationResultException("时间格式应为 yyyy-MM-dd HH:mm:ss，实际为：" + value);
    }

    @SuppressWarnings("unchecked")
    private List<Object> toList(Object value, String key) {
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>((Collection<Object>) collection);
        }
        throw new AiValidationResultException("字段 " + key + " 必须为数组");
    }

    private List<String> toStringList(Object value, String key) {
        List<Object> list = toList(value, key);
        return list.stream().map(String::valueOf).toList();
    }

    private static class AiValidationResultException extends RuntimeException {
        AiValidationResultException(String message) {
            super(message);
        }
    }
}
