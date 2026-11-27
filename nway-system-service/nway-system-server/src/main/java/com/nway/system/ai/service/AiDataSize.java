package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiOutputField;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据体量测量：统一计算 rows / chars / bytes / total / hasNext，并给出展示、分析、导出口径的判定。
 * <p>
 * 所有判定都在服务端完成，模型只看到判定结果。行数优先按 outputSchema 的数组字段提取，
 * 否则取 data 中第一个业务数组字段（records/list/rows/items）。
 *
 * @author nway
 */
@Component
public class AiDataSize {

    private static final Set<String> ENVELOPE_KEYS = Set.of(
            "code", "msg", "message", "success", "error", "errorCode", "timestamp", "traceId", "requestId");

    private static final List<String> ROW_KEYS = List.of("records", "list", "rows", "items", "data");

    private static final List<String> TOTAL_KEYS = List.of(
            "total", "totalCount", "totalElements", "totalSize", "totalNum", "count");

    public record Size(long rows, long chars, long bytes, Long total, boolean hasNext) {
    }

    /**
     * 直查展示数据与是否发生裁剪。
     */
    public record DisplayData(Object data, boolean truncated) {
    }

    /**
     * 直查展示降级：先按行数裁剪，仍超字符上限时继续减半，单行仍超限则不给数据。
     * <p>
     * 展示链路允许部分数据 + truncated 警告；分析链路不使用本方法。
     */
    public DisplayData fitForDisplay(Object data, List<AiOutputField> outputSchema, AiLimits limits) {
        if (data == null) {
            return new DisplayData(null, false);
        }
        int maxRows = Math.max(1, limits.getDisplayMaxRows());
        Object current = data;
        boolean truncated = false;
        if (measure(current).rows() > maxRows) {
            current = trimForDisplay(current, outputSchema, maxRows);
            truncated = true;
        }
        while (chars(current) > limits.getDisplayMaxChars() && maxRows > 1) {
            maxRows = Math.max(1, maxRows / 2);
            current = trimForDisplay(data, outputSchema, maxRows);
            truncated = true;
        }
        if (chars(current) > limits.getDisplayMaxChars()) {
            // 单行文本仍超展示上限：放弃展示，避免把超大报文塞给前端
            return new DisplayData(null, true);
        }
        return new DisplayData(current, truncated);
    }

    /**
     * 测量结果。chars 按 JSON 序列化计算，与进入模型上下文的字符量口径一致。
     */
    public Size measure(Object data) {
        if (data == null) {
            return new Size(0, 0, 0, null, false);
        }
        RowLocation rows = findRows(data, null, 0);
        long rowCount = rows == null ? 0 : rows.rows().size();
        String json = JSON.toJSONString(data);
        long chars = json.length();
        long bytes = json.getBytes(StandardCharsets.UTF_8).length;
        return new Size(rowCount, chars, bytes, total(data), hasNext(data));
    }

    public long chars(Object data) {
        return data == null ? 0 : JSON.toJSONString(data).length();
    }

    /**
     * 按出参定义提取业务行；没有可识别数组时返回空列表。
     */
    public List<Object> extractRows(Object data, List<AiOutputField> outputSchema) {
        RowLocation location = findRows(data, outputSchema, 0);
        return location == null ? List.of() : location.rows();
    }

    /**
     * 接口总记录数：优先 total，其次同义字段；找不到时返回 null。
     */
    public Long total(Object data) {
        Object value = unwrapEnvelope(data);
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Long direct = totalIn(map);
        if (direct != null) {
            return direct;
        }
        for (String key : List.of("page", "pagination")) {
            if (map.get(key) instanceof Map<?, ?> nested) {
                Long nestedTotal = totalIn(nested);
                if (nestedTotal != null) {
                    return nestedTotal;
                }
            }
        }
        return null;
    }

    /**
     * 是否还有下一页：显式 hasNext 优先，其次 currentPage < totalPages。
     */
    public boolean hasNext(Object data) {
        Object value = unwrapEnvelope(data);
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        for (String key : List.of("hasNext", "hasNextPage", "hasMore")) {
            Object flag = map.get(key);
            if (flag instanceof Boolean bool) {
                return bool;
            }
            if (flag != null && "true".equalsIgnoreCase(String.valueOf(flag))) {
                return true;
            }
        }
        Long current = numeric(map.get("currentPage") != null ? map.get("currentPage") : map.get("pageNum"));
        Long pages = numeric(map.get("totalPages") != null ? map.get("totalPages") : map.get("pages"));
        return current != null && pages != null && current < pages;
    }

    public boolean exceedsAnalysis(Size size, AiLimits limits) {
        if (size == null) {
            return false;
        }
        return size.chars() > limits.getAnalysisMaxChars()
                || size.rows() > limits.getAnalysisMaxRows()
                || (size.total() != null && size.total() > limits.getAnalysisMaxTotal());
    }

    public boolean exceedsDisplay(Size size, AiLimits limits) {
        if (size == null) {
            return false;
        }
        return size.rows() > limits.getDisplayMaxRows() || size.chars() > limits.getDisplayMaxChars();
    }

    /**
     * 直查展示降级：只截取前 N 行，其余保留在接口侧；返回的结构仍可渲染。
     * <p>
     * 入参 data 来自本次调用的临时结果，允许原地裁剪。
     */
    public Object trimForDisplay(Object data, List<AiOutputField> outputSchema, int maxRows) {
        if (data == null || maxRows <= 0) {
            return data;
        }
        if (data instanceof Collection<?> collection) {
            if (collection.size() <= maxRows) {
                return data;
            }
            return new ArrayList<>(new ArrayList<>(collection).subList(0, maxRows));
        }
        RowLocation location = findRows(data, outputSchema, 0);
        if (location == null || location.rows().size() <= maxRows || location.holder() == null) {
            return data;
        }
        List<Object> kept = new ArrayList<>(location.rows().subList(0, maxRows));
        try {
            location.holder().put(location.key(), kept);
            return data;
        } catch (UnsupportedOperationException e) {
            // 不可变结构（如 Map.of）无法原地裁剪：复制容器后返回新的结构
            if (data == location.holder()) {
                Map<String, Object> copy = new LinkedHashMap<>(location.holder());
                copy.put(location.key(), kept);
                return copy;
            }
            return data;
        }
    }

    /**
     * 行数据所在位置：holder+key 用于裁剪时原地替换；holder 为 null 表示整份 data 就是数组。
     */
    private record RowLocation(List<Object> rows, Map<String, Object> holder, String key) {
    }

    @SuppressWarnings("unchecked")
    private RowLocation findRows(Object data, List<AiOutputField> outputSchema, int depth) {
        Object value = unwrapEnvelope(data);
        if (value instanceof Collection<?> collection) {
            return new RowLocation(new ArrayList<>((Collection<Object>) collection), null, null);
        }
        if (!(value instanceof Map<?, ?> rawMap) || depth > 2) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        String schemaKey = schemaArrayKey(outputSchema);
        if (schemaKey != null && map.get(schemaKey) instanceof Collection<?> collection) {
            return new RowLocation(new ArrayList<>((Collection<Object>) collection), map, schemaKey);
        }
        for (String key : ROW_KEYS) {
            if (map.get(key) instanceof Collection<?> collection) {
                return new RowLocation(new ArrayList<>((Collection<Object>) collection), map, key);
            }
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Collection<?> collection) {
                return new RowLocation(new ArrayList<>((Collection<Object>) collection), map, entry.getKey());
            }
        }
        // 再向下找一层：部分接口把分页结构包在 data/page 中
        for (String key : List.of("data", "page", "pagination", "result")) {
            Object nested = map.get(key);
            if (nested instanceof Map<?, ?>) {
                RowLocation location = findRows(nested, outputSchema, depth + 1);
                if (location != null) {
                    return location;
                }
            }
        }
        return null;
    }

    private String schemaArrayKey(List<AiOutputField> outputSchema) {
        if (outputSchema == null) {
            return null;
        }
        for (AiOutputField field : outputSchema) {
            if (field == null || StringUtils.isBlank(field.getKey())) {
                continue;
            }
            String type = StringUtils.upperCase(field.getType());
            if ("ARRAY".equals(type) || field.getItems() != null) {
                return field.getKey();
            }
        }
        return null;
    }

    /**
     * 脱掉 code/msg/data 一类信封，拿到真正的业务数据。
     */
    private Object unwrapEnvelope(Object data) {
        Object value = data;
        for (int i = 0; i < 3; i++) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey("data")) {
                return value;
            }
            boolean envelope = map.keySet().stream()
                    .allMatch(key -> "data".equals(String.valueOf(key)) || ENVELOPE_KEYS.contains(String.valueOf(key)));
            if (!envelope) {
                return value;
            }
            Object inner = map.get("data");
            if (inner == null) {
                return value;
            }
            value = inner;
        }
        return value;
    }

    private Long totalIn(Map<?, ?> map) {
        for (String key : TOTAL_KEYS) {
            Long value = numeric(map.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long numeric(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.isNumeric(text.trim())) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    /** 导出列定义：优先 outputSchema 的数组元素字段，其次从首行推断。 */
    public List<Map<String, Object>> columns(Object sampleRow, List<AiOutputField> outputSchema) {
        List<Map<String, Object>> columns = new ArrayList<>();
        AiOutputField arrayField = arrayField(outputSchema);
        if (arrayField != null && arrayField.getItems() != null
                && arrayField.getItems().getProperties() != null
                && !arrayField.getItems().getProperties().isEmpty()) {
            for (AiOutputField field : arrayField.getItems().getProperties()) {
                if (field == null || StringUtils.isBlank(field.getKey())) {
                    continue;
                }
                Map<String, Object> column = new LinkedHashMap<>();
                column.put("key", field.getKey());
                column.put("label", StringUtils.defaultIfBlank(field.getLabel(), field.getKey()));
                columns.add(column);
            }
            return columns;
        }
        if (sampleRow instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                Map<String, Object> column = new LinkedHashMap<>();
                column.put("key", String.valueOf(key));
                column.put("label", String.valueOf(key));
                columns.add(column);
            }
            return columns;
        }
        if (sampleRow != null) {
            // 标量行（如 ["A","B"]）：统一按单列展示
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("key", "value");
            column.put("label", "值");
            columns.add(column);
        }
        return columns;
    }

    private AiOutputField arrayField(List<AiOutputField> outputSchema) {
        if (outputSchema == null) {
            return null;
        }
        for (AiOutputField field : outputSchema) {
            if (field == null) {
                continue;
            }
            if ("ARRAY".equals(StringUtils.upperCase(field.getType())) || field.getItems() != null) {
                return field;
            }
        }
        return null;
    }
}
