package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.web.model.AiConversationExportRequest;
import com.nway.system.ai.web.model.AiExportRequest;
import com.nway.system.ai.web.model.AiExportVo;
import com.nway.system.ai.web.model.AiMessageVo;
import com.nway.system.common.tenant.TenantUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话导出：直接读取 ai_message 生成文件，不重新调用大模型分析。
 * <p>
 * 用户消息保留正文与附件；助手消息保留正文（含 nway-view 图表/指标），
 * 工具结果转为表格；thinking、页面上下文、图片占位与原始 JSON 默认排除。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiConversationExportService {

    private static final Pattern NWAY_VIEW = Pattern.compile(
            "```[ \\t]*nway-view[^\\n]*\\r?\\n([\\s\\S]*?)```");
    private static final String PAGE_CONTEXT_MARKER = "【当前页面上下文】";
    private static final int MAX_RESULT_ROWS = 200;
    private static final int MAX_BLOCKS = 190;
    private static final int MAX_TEXT_CHARS = 190_000;
    private static final int MAX_ARGS_CHARS = 300;
    private static final String DEFAULT_TITLE = "AI 对话记录";

    private final AiConversationHistoryReader historyReader;
    private final AiExportService exportService;
    private final AiDataSize dataSize;
    private final AiLimits limits;

    public AiConversationExportService(AiConversationHistoryReader historyReader,
                                       AiExportService exportService, AiDataSize dataSize,
                                       AiLimits limits) {
        this.historyReader = historyReader;
        this.exportService = exportService;
        this.dataSize = dataSize;
        this.limits = limits;
    }

    /**
     * 会话内容超过导出上限时抛出，由调用方转为 handoff / 用户提示。
     */
    public static class ConversationTooLargeException extends RuntimeException {
        public ConversationTooLargeException(String message) {
            super(message);
        }
    }

    public AiExportVo export(AiUserContext user, String conversationId,
                             AiConversationExportRequest request) {
        String cid = StringUtils.defaultIfBlank(conversationId,
                user == null ? null : user.getConversationId());
        if (StringUtils.isBlank(cid)) {
            throw new ConversationTooLargeException("缺少会话标识，无法导出");
        }
        AiConversationExportRequest options = request == null ? new AiConversationExportRequest() : request;
        List<AiMessageVo> messages = historyReader.list(user, cid);
        List<AiMessageVo> selected = select(messages, options);
        if (selected.isEmpty()) {
            throw new ConversationTooLargeException("当前会话没有可导出的消息");
        }
        if (selected.size() > limits.getConversationMaxMessages()) {
            throw new ConversationTooLargeException("会话消息过多（超过 " + limits.getConversationMaxMessages()
                    + " 条），请缩小范围或分卷导出");
        }

        boolean includeToolResults = options.getIncludeToolResults() == null
                || options.getIncludeToolResults();
        boolean includeAttachments = options.getIncludeAttachments() == null
                || options.getIncludeAttachments();

        List<AiExportRequest.Block> blocks = new ArrayList<>();
        for (AiMessageVo message : selected) {
            boolean first = true;
            for (Map<String, Object> block : message.getBlocks() == null ? List.<Map<String, Object>>of()
                    : message.getBlocks()) {
                String type = String.valueOf(block.get("type"));
                switch (type) {
                    case "text" -> {
                        String text = String.valueOf(block.get("text"));
                        if (text.startsWith(PAGE_CONTEXT_MARKER)) {
                            continue;
                        }
                        if (first) {
                            blocks.add(textBlock("user".equals(message.getRole()) ? "### 用户" : "### AI 助手"));
                            first = false;
                        }
                        appendTextWithViews(blocks, text);
                    }
                    case "thinking" -> {
                        // 默认排除思考过程
                    }
                    case "attachments" -> {
                        if (!includeAttachments) {
                            continue;
                        }
                        appendAttachments(blocks, block);
                    }
                    case "tool_use" -> {
                        if (!includeToolResults) {
                            continue;
                        }
                        appendToolResult(blocks, block);
                    }
                    default -> {
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            throw new ConversationTooLargeException("当前会话没有可导出的内容");
        }
        if (blocks.size() > MAX_BLOCKS) {
            throw new ConversationTooLargeException("会话内容过多，请缩小导出范围后重试");
        }
        long chars = blocks.stream()
                .mapToLong(b -> b.getText() == null ? 0 : b.getText().length())
                .sum();
        if (chars > MAX_TEXT_CHARS || chars > limits.getConversationMaxChars()) {
            throw new ConversationTooLargeException("会话内容过长，请缩小导出范围或分卷导出");
        }

        AiExportRequest exportRequest = new AiExportRequest();
        exportRequest.setFormat(StringUtils.defaultIfBlank(options.getFormat(), "docx"));
        exportRequest.setTitle(DEFAULT_TITLE);
        exportRequest.setConversationId(cid);
        exportRequest.setBlocks(blocks);

        String tenant = user == null ? null : user.effectiveTenantCode();
        if (StringUtils.isBlank(tenant)) {
            return exportService.export(exportRequest);
        }
        Object[] holder = new Object[1];
        TenantUtil.runAsTenant(tenant, () -> holder[0] = exportService.export(exportRequest));
        return (AiExportVo) holder[0];
    }

    /**
     * 导出单次工具结果：原结果被截断或 handoff 时拒绝生成文件。
     */
    @SuppressWarnings("unchecked")
    public AiExportVo exportToolResult(AiUserContext user, Map<String, Object> toolBlock,
                                       String format, String title) {
        Object raw = toolBlock == null ? null : toolBlock.get("result");
        if (!(raw instanceof String text) || StringUtils.isBlank(text)) {
            throw new ConversationTooLargeException("没有可导出的查询结果");
        }
        Map<String, Object> parsed;
        try {
            Object value = JSON.parse(text);
            if (!(value instanceof Map<?, ?> map)) {
                throw new ConversationTooLargeException("原结果不是可导出的结构化数据");
            }
            parsed = (Map<String, Object>) map;
        } catch (ConversationTooLargeException e) {
            throw e;
        } catch (Exception e) {
            throw new ConversationTooLargeException("原结果不是可导出的结构化数据");
        }
        if (parsed.get("handoff") != null || Boolean.TRUE.equals(parsed.get("truncated"))) {
            throw new ConversationTooLargeException(
                    "原查询结果不完整（已截断或超出在线处理上限），无法导出；请改用大数据导出重新查询");
        }
        if (parsed.get("data") == null && !(parsed.get("results") instanceof List<?>)) {
            throw new ConversationTooLargeException("原查询结果没有可导出的数据");
        }
        List<AiExportRequest.Block> blocks = new ArrayList<>();
        appendToolResult(blocks, toolBlock);
        if (blocks.isEmpty()) {
            throw new ConversationTooLargeException("原查询结果没有可导出的数据");
        }
        AiExportRequest exportRequest = new AiExportRequest();
        exportRequest.setFormat(StringUtils.defaultIfBlank(format, "xlsx"));
        exportRequest.setTitle(StringUtils.defaultIfBlank(title, "查询结果"));
        exportRequest.setConversationId(user == null ? null : user.getConversationId());
        exportRequest.setBlocks(blocks);
        String tenant = user == null ? null : user.effectiveTenantCode();
        if (StringUtils.isBlank(tenant)) {
            return exportService.export(exportRequest);
        }
        Object[] holder = new Object[1];
        TenantUtil.runAsTenant(tenant, () -> holder[0] = exportService.export(exportRequest));
        return (AiExportVo) holder[0];
    }

    // ==================== 消息筛选 ====================

    private List<AiMessageVo> select(List<AiMessageVo> messages, AiConversationExportRequest options) {
        List<AiMessageVo> result = new ArrayList<>(messages);
        String scope = StringUtils.upperCase(StringUtils.defaultIfBlank(options.getScope(), "ALL"));
        if ("LAST_N".equals(scope) && options.getLastN() != null && options.getLastN() > 0) {
            int from = Math.max(0, result.size() - options.getLastN());
            result = new ArrayList<>(result.subList(from, result.size()));
        } else if ("SELECTED".equals(scope) && options.getMessageIds() != null
                && !options.getMessageIds().isEmpty()) {
            List<AiMessageVo> filtered = new ArrayList<>();
            for (AiMessageVo message : result) {
                if (options.getMessageIds().contains(message.getId())) {
                    filtered.add(message);
                }
            }
            result = filtered;
        }
        if (options.getTimeRange() != null) {
            String start = options.getTimeRange().getStart();
            String end = options.getTimeRange().getEnd();
            result = result.stream().filter(message -> inRange(message.getTimestamp(), start, end)).toList();
        }
        return result;
    }

    private boolean inRange(String timestamp, String start, String end) {
        if (StringUtils.isBlank(start) && StringUtils.isBlank(end)) {
            return true;
        }
        if (StringUtils.isBlank(timestamp) || timestamp.length() < 10) {
            return true;
        }
        String date = timestamp.substring(0, 10);
        if (StringUtils.isNotBlank(start) && date.compareTo(start) < 0) {
            return false;
        }
        return StringUtils.isBlank(end) || date.compareTo(end) <= 0;
    }

    // ==================== Block 转换 ====================

    private AiExportRequest.Block textBlock(String text) {
        AiExportRequest.Block block = new AiExportRequest.Block();
        block.setType("text");
        block.setText(text);
        return block;
    }

    /**
     * 正文中的 nway-view 代码块转为结构化视图，其余文本原样保留。
     */
    private void appendTextWithViews(List<AiExportRequest.Block> blocks, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        Matcher matcher = NWAY_VIEW.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            String before = text.substring(cursor, matcher.start()).trim();
            if (!before.isEmpty()) {
                blocks.add(textBlock(before));
            }
            AiExportRequest.View view = toView(matcher.group(1));
            if (view != null) {
                AiExportRequest.Block block = new AiExportRequest.Block();
                block.setType("view");
                block.setView(view);
                blocks.add(block);
            } else {
                blocks.add(textBlock(matcher.group(0)));
            }
            cursor = matcher.end();
        }
        String tail = text.substring(cursor).trim();
        if (!tail.isEmpty()) {
            blocks.add(textBlock(tail));
        }
    }

    @SuppressWarnings("unchecked")
    private AiExportRequest.View toView(String json) {
        Object parsed;
        try {
            parsed = JSON.parse(json);
        } catch (Exception e) {
            return null;
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        String type = StringUtils.lowerCase(String.valueOf(map.get("type")), java.util.Locale.ROOT);
        AiExportRequest.View view = new AiExportRequest.View();
        view.setType(type);
        view.setTitle(string(map.get("title")));
        switch (type) {
            case "table" -> {
                view.setColumns(columns(map.get("columns")));
                view.setRows(map.get("rows") instanceof List<?> list
                        ? new ArrayList<>((List<Object>) list) : null);
                view.setTotal(map.get("total") instanceof Number number ? number.intValue() : null);
                view.setSummary(metrics(map.get("summary")));
            }
            case "metric" -> view.setMetrics(metrics(
                    map.get("metrics") instanceof List<?> list ? list : map.get("summary")));
            case "chart" -> view.setChart(chart(map.get("chart")));
            case "fields" -> view.setFields(fields(map.get("fields")));
            case "list" -> {
                Object items = map.get("items") != null ? map.get("items") : map.get("list");
                view.setList(items instanceof List<?> list
                        ? new ArrayList<>((List<Object>) list) : null);
            }
            case "file" -> view.setFiles(files(map.get("files")));
            case "text" -> view.setText(string(map.get("text")));
            default -> {
                return null;
            }
        }
        return view;
    }

    @SuppressWarnings("unchecked")
    private List<AiExportRequest.Column> columns(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<AiExportRequest.Column> columns = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            AiExportRequest.Column column = new AiExportRequest.Column();
            column.setKey(string(map.get("key")));
            column.setLabel(string(map.get("label")));
            if (column.getKey() != null) {
                columns.add(column);
            }
        }
        return columns;
    }

    @SuppressWarnings("unchecked")
    private List<AiExportRequest.Metric> metrics(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<AiExportRequest.Metric> metrics = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            AiExportRequest.Metric metric = new AiExportRequest.Metric();
            metric.setLabel(string(map.get("label")));
            metric.setValue(string(map.get("value")));
            metric.setUnit(string(map.get("unit")));
            metric.setTrend(string(map.get("trend")));
            metrics.add(metric);
        }
        return metrics;
    }

    @SuppressWarnings("unchecked")
    private List<AiExportRequest.Field> fields(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<AiExportRequest.Field> fields = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            AiExportRequest.Field field = new AiExportRequest.Field();
            field.setKey(string(map.get("key")));
            field.setLabel(string(map.get("label")));
            field.setValue(map.get("value"));
            if (field.getKey() != null) {
                fields.add(field);
            }
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private List<AiExportRequest.FileItem> files(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<AiExportRequest.FileItem> files = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            AiExportRequest.FileItem file = new AiExportRequest.FileItem();
            file.setName(string(map.get("name")));
            file.setUrl(string(map.get("url")));
            file.setSize(string(map.get("size")));
            files.add(file);
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    private AiExportRequest.Chart chart(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        AiExportRequest.Chart chart = new AiExportRequest.Chart();
        chart.setKind(string(map.get("kind")));
        if (map.get("categories") instanceof List<?> categories) {
            chart.setCategories(categories.stream().map(String::valueOf).toList());
        }
        if (map.get("series") instanceof List<?> seriesList) {
            List<AiExportRequest.Series> series = new ArrayList<>();
            for (Object item : seriesList) {
                if (!(item instanceof Map<?, ?> seriesMap)) {
                    continue;
                }
                AiExportRequest.Series one = new AiExportRequest.Series();
                one.setName(string(seriesMap.get("name")));
                if (seriesMap.get("data") instanceof List<?> data) {
                    one.setData(data.stream()
                            .map(value -> value instanceof Number number ? number.doubleValue() : 0d)
                            .toList());
                }
                series.add(one);
            }
            chart.setSeries(series);
        }
        return chart;
    }

    private void appendAttachments(List<AiExportRequest.Block> blocks, Map<String, Object> block) {
        if (!(block.get("files") instanceof List<?> files) || files.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("附件：");
        for (Object item : files) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = StringUtils.defaultIfBlank(string(map.get("name")), "文件");
            String url = string(map.get("previewUrl") != null ? map.get("previewUrl") : map.get("url"));
            sb.append("\n- ").append(name);
            if (StringUtils.isNotBlank(url)) {
                sb.append("：").append(url);
            }
        }
        blocks.add(textBlock(sb.toString()));
    }

    /**
     * 工具结果转为可读内容：接口名、参数摘要与结果表格；原始 JSON 不导出。
     */
    @SuppressWarnings("unchecked")
    private void appendToolResult(List<AiExportRequest.Block> blocks, Map<String, Object> block) {
        String name = String.valueOf(block.get("name"));
        if ("get_interface_config".equals(name) || "search_interfaces".equals(name)) {
            return;
        }
        Object rawResult = block.get("result");
        if (!(rawResult instanceof String result) || StringUtils.isBlank(result)) {
            return;
        }
        Map<String, Object> parsed;
        try {
            Object value = JSON.parse(result);
            if (!(value instanceof Map<?, ?> map)) {
                return;
            }
            parsed = (Map<String, Object>) map;
        } catch (Exception e) {
            return;
        }
        String interfaceName = StringUtils.defaultIfBlank(string(parsed.get("interfaceName")),
                StringUtils.defaultIfBlank(string(parsed.get("interfaceCode")), name));
        Object input = block.get("input");
        if (input instanceof Map<?, ?> args && !args.isEmpty()) {
            blocks.add(textBlock("工具：" + interfaceName + "\n参数："
                    + StringUtils.abbreviate(JSON.toJSONString(args), MAX_ARGS_CHARS)));
        } else {
            blocks.add(textBlock("工具：" + interfaceName));
        }
        if (parsed.get("handoff") != null) {
            blocks.add(textBlock("结果：数据量超出在线处理上限，未导出数据。"));
            return;
        }
        if (parsed.get("error") != null) {
            blocks.add(textBlock("结果：失败 - " + StringUtils.defaultIfBlank(
                    string(parsed.get("message")), String.valueOf(parsed.get("error")))));
            return;
        }
        if (parsed.get("data") != null) {
            appendData(blocks, parsed.get("data"), interfaceName, Boolean.TRUE.equals(parsed.get("truncated")));
        } else if (parsed.get("results") instanceof List<?> results) {
            for (Object item : results) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String label = StringUtils.defaultIfBlank(string(map.get("label")),
                        StringUtils.defaultIfBlank(string(map.get("interfaceName")), name));
                if (map.get("data") != null) {
                    appendData(blocks, map.get("data"), label, Boolean.TRUE.equals(map.get("truncated")));
                } else if (map.get("message") != null) {
                    blocks.add(textBlock("查询：" + label + " - " + map.get("message")));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void appendData(List<AiExportRequest.Block> blocks, Object data, String title, boolean truncated) {
        List<Object> rows = dataSize.extractRows(data, null);
        if (!rows.isEmpty()) {
            if (rows.size() > MAX_RESULT_ROWS) {
                rows = new ArrayList<>(rows.subList(0, MAX_RESULT_ROWS));
                truncated = true;
            }
            List<Map<String, Object>> columns = dataSize.columns(rows.get(0), null);
            AiExportRequest.View view = new AiExportRequest.View();
            view.setType("table");
            view.setTitle(title);
            List<AiExportRequest.Column> columnDefs = new ArrayList<>();
            for (Map<String, Object> column : columns) {
                AiExportRequest.Column def = new AiExportRequest.Column();
                def.setKey(String.valueOf(column.get("key")));
                def.setLabel(String.valueOf(column.get("label")));
                columnDefs.add(def);
            }
            view.setColumns(columnDefs);
            view.setRows(new ArrayList<>(rows));
            view.setTotal(rows.size());
            AiExportRequest.Block block = new AiExportRequest.Block();
            block.setType("view");
            block.setView(view);
            blocks.add(block);
            if (truncated) {
                blocks.add(textBlock("（数据不完整：仅展示前 " + MAX_RESULT_ROWS
                        + " 条或原结果已被截断，完整数据请通过对应菜单导出）"));
            }
            return;
        }
        if (data instanceof Map<?, ?> map && !map.isEmpty()) {
            AiExportRequest.View view = new AiExportRequest.View();
            view.setType("fields");
            view.setTitle(title);
            List<AiExportRequest.Field> fields = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                    continue;
                }
                AiExportRequest.Field field = new AiExportRequest.Field();
                field.setKey(String.valueOf(entry.getKey()));
                field.setLabel(String.valueOf(entry.getKey()));
                field.setValue(entry.getValue());
                fields.add(field);
            }
            if (!fields.isEmpty()) {
                view.setFields(fields);
                AiExportRequest.Block block = new AiExportRequest.Block();
                block.setType("view");
                block.setView(view);
                blocks.add(block);
            }
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 供工具/控制器复用的导出结果统计（当前仅记录日志）。
     */
    public Map<String, Object> summarize(AiExportVo vo) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fileId", vo.getFileId());
        summary.put("fileName", vo.getFileName());
        summary.put("size", vo.getSize());
        return summary;
    }
}
