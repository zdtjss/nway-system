package com.nway.system.ai.export;

import com.nway.system.ai.web.model.AiExportRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 单文件 HTML 报告：内联样式与 SVG 图表，不依赖任何外部资源，浏览器直接打开。
 *
 * @author nway
 */
public final class AiExportHtmlWriter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AiExportHtmlWriter() {
    }

    public static byte[] write(AiExportRequest request, String title, LocalDateTime generatedAt) {
        StringBuilder html = new StringBuilder(16_384);
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(escape(title)).append("</title><style>").append(style())
                .append("</style></head><body><article class=\"report\">")
                .append("<header class=\"report-head\"><h1 class=\"report-title\">").append(escape(title)).append("</h1>")
                .append("<p class=\"report-meta\">由 AI 助手生成 · ")
                .append(escape(TIME.format(generatedAt))).append("</p></header><main>");
        for (AiExportRequest.Block block : request.getBlocks()) {
            if (block == null) {
                continue;
            }
            if ("view".equalsIgnoreCase(block.getType()) && block.getView() != null) {
                appendView(html, block.getView());
            } else if (block.getText() != null && !block.getText().isBlank()) {
                html.append(renderMarkdown(block.getText()));
            }
        }
        html.append("</main><footer class=\"report-foot\">本报告由 AI 助手生成，内容仅供参考。</footer>")
                .append("</article></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== 正文 ====================

    private static String renderMarkdown(String markdown) {
        List<AiExportMarkdown.MdBlock> blocks = AiExportMarkdown.parse(markdown);
        StringBuilder html = new StringBuilder();
        int index = 0;
        while (index < blocks.size()) {
            AiExportMarkdown.MdBlock block = blocks.get(index);
            switch (block.type()) {
                case HEADING -> {
                    int level = Math.min(6, Math.max(2, block.level() + 1));
                    html.append("<h").append(level).append('>').append(inlineHtml(block.text()))
                            .append("</h").append(level).append('>');
                    index++;
                }
                case PARAGRAPH -> {
                    html.append("<p>").append(inlineHtml(block.text()).replace("\n", "<br>")).append("</p>");
                    index++;
                }
                case BULLET -> {
                    html.append("<ul>");
                    while (index < blocks.size() && blocks.get(index).type() == AiExportMarkdown.BlockType.BULLET) {
                        html.append("<li>").append(inlineHtml(blocks.get(index).text())).append("</li>");
                        index++;
                    }
                    html.append("</ul>");
                }
                case ORDERED -> {
                    html.append("<ol>");
                    while (index < blocks.size() && blocks.get(index).type() == AiExportMarkdown.BlockType.ORDERED) {
                        html.append("<li>").append(inlineHtml(blocks.get(index).text())).append("</li>");
                        index++;
                    }
                    html.append("</ol>");
                }
                case CODE -> {
                    html.append("<pre class=\"code\"><code>").append(escape(block.text())).append("</code></pre>");
                    index++;
                }
                case QUOTE -> {
                    html.append("<blockquote>").append(inlineHtml(block.text()).replace("\n", "<br>"))
                            .append("</blockquote>");
                    index++;
                }
                case TABLE -> {
                    html.append("<div class=\"table-wrap\"><table>");
                    List<List<String>> rows = block.table();
                    if (!rows.isEmpty()) {
                        html.append("<thead><tr>");
                        rows.get(0).forEach(cell -> html.append("<th>").append(inlineHtml(cell)).append("</th>"));
                        html.append("</tr></thead><tbody>");
                        for (int r = 1; r < rows.size(); r++) {
                            html.append("<tr>");
                            rows.get(r).forEach(cell -> html.append("<td>").append(inlineHtml(cell)).append("</td>"));
                            html.append("</tr>");
                        }
                        html.append("</tbody>");
                    }
                    html.append("</table></div>");
                    index++;
                }
                case HR -> {
                    html.append("<hr>");
                    index++;
                }
            }
        }
        return html.toString();
    }

    private static String inlineHtml(String text) {
        StringBuilder html = new StringBuilder();
        for (AiExportMarkdown.Inline inline : AiExportMarkdown.inline(text)) {
            switch (inline.type()) {
                case BOLD -> html.append("<strong>").append(escape(inline.text())).append("</strong>");
                case ITALIC -> html.append("<em>").append(escape(inline.text())).append("</em>");
                case CODE -> html.append("<code>").append(escape(inline.text())).append("</code>");
                case STRIKE -> html.append("<del>").append(escape(inline.text())).append("</del>");
                case LINK -> {
                    String href = safeHref(inline.href());
                    if (href == null) {
                        html.append(escape(inline.text()));
                    } else {
                        html.append("<a href=\"").append(escape(href)).append("\" target=\"_blank\" rel=\"noopener\">")
                                .append(escape(inline.text())).append("</a>");
                    }
                }
                default -> html.append(escape(inline.text()));
            }
        }
        return html.toString();
    }

    // ==================== 结构化视图 ====================

    private static void appendView(StringBuilder html, AiExportRequest.View view) {
        String type = view.getType() == null ? "" : view.getType();
        if (view.getTitle() != null && !view.getTitle().isBlank()) {
            html.append("<h3 class=\"view-title\">").append(escape(view.getTitle())).append("</h3>");
        }
        switch (type) {
            case "table" -> appendTable(html, view);
            case "metric" -> appendMetrics(html, view.getMetrics());
            case "chart" -> appendChart(html, view);
            case "fields" -> appendFields(html, view.getFields());
            case "list" -> appendList(html, view.listCells());
            case "file" -> appendFiles(html, view.getFiles());
            case "text" -> html.append("<p class=\"text\">").append(escape(view.getText()).replace("\n", "<br>"))
                    .append("</p>");
            default -> html.append("<p class=\"tip\">")
                    .append(escape(view.getMessage() == null || view.getMessage().isBlank()
                            ? "接口未返回数据" : view.getMessage())).append("</p>");
        }
    }

    private static void appendTable(StringBuilder html, AiExportRequest.View view) {
        if (view.getSummary() != null && !view.getSummary().isEmpty()) {
            appendMetricChips(html, view.getSummary());
        }
        List<AiExportRequest.Column> columns = view.effectiveColumns();
        List<List<AiExportRequest.Cell>> rows = view.tableRows();
        html.append("<div class=\"table-wrap\"><table><thead><tr>");
        List<String> labels = new ArrayList<>();
        for (AiExportRequest.Column column : columns) {
            labels.add(column.getLabel() == null || column.getLabel().isBlank() ? column.getKey() : column.getLabel());
            html.append("<th>").append(escape(labels.get(labels.size() - 1))).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        if (rows.isEmpty()) {
            html.append("<tr><td class=\"empty\" colspan=\"").append(Math.max(1, columns.size()))
                    .append("\">暂无数据</td></tr>");
        }
        for (List<AiExportRequest.Cell> row : rows) {
            html.append("<tr>");
            for (AiExportRequest.Cell cell : row) {
                html.append("<td>").append(cellHtml(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");
        if (view.getTotal() != null && view.getTotal() > rows.size()) {
            html.append("<p class=\"tip\">共 ").append(view.getTotal()).append(" 条，页面展示前 ")
                    .append(rows.size()).append(" 条</p>");
        }
        if (Boolean.TRUE.equals(view.getTruncated())) {
            html.append("<p class=\"tip\">数据较多，已按页面展示范围导出</p>");
        }
    }

    private static void appendMetrics(StringBuilder html, List<AiExportRequest.Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        html.append("<div class=\"metric-grid\">");
        for (AiExportRequest.Metric metric : metrics) {
            html.append("<div class=\"metric\"><div class=\"metric-label\">").append(escape(metric.getLabel()))
                    .append("</div><div class=\"metric-value\">").append(escape(metric.getValue()));
            if (metric.getUnit() != null && !metric.getUnit().isBlank()) {
                html.append("<span class=\"metric-unit\">").append(escape(metric.getUnit())).append("</span>");
            }
            html.append("</div>");
            if (metric.getTrend() != null && !metric.getTrend().isBlank()) {
                html.append("<div class=\"metric-trend\">").append(escape(metric.getTrend())).append("</div>");
            }
            html.append("</div>");
        }
        html.append("</div>");
    }

    private static void appendMetricChips(StringBuilder html, List<AiExportRequest.Metric> metrics) {
        html.append("<div class=\"metric-chips\">");
        for (AiExportRequest.Metric metric : metrics) {
            html.append("<span class=\"chip\"><span class=\"chip-label\">").append(escape(metric.getLabel()))
                    .append("</span>").append(escape(metric.getValue()));
            if (metric.getUnit() != null && !metric.getUnit().isBlank()) {
                html.append(escape(metric.getUnit()));
            }
            html.append("</span>");
        }
        html.append("</div>");
    }

    private static void appendChart(StringBuilder html, AiExportRequest.View view) {
        if (view.getChart() == null) {
            html.append("<p class=\"tip\">暂无图表数据</p>");
            return;
        }
        html.append("<div class=\"chart-wrap\">").append(AiExportCharts.renderSvg(view.getChart())).append("</div>");
        // 图表下方附数据表，保证数字可被复制核对
        List<String> categories = view.getChart().getCategories() == null
                ? List.of() : view.getChart().getCategories();
        List<AiExportRequest.Series> series = view.getChart().getSeries() == null
                ? List.of() : view.getChart().getSeries();
        if (!categories.isEmpty() && !series.isEmpty()) {
            html.append("<div class=\"table-wrap chart-data\"><table><thead><tr><th>分类</th>");
            for (AiExportRequest.Series item : series) {
                html.append("<th>").append(escape(item.getName())).append("</th>");
            }
            html.append("</tr></thead><tbody>");
            for (int i = 0; i < categories.size(); i++) {
                html.append("<tr><td>").append(escape(categories.get(i))).append("</td>");
                for (AiExportRequest.Series item : series) {
                    List<Double> data = item.getData();
                    Double value = data != null && i < data.size() ? data.get(i) : null;
                    html.append("<td>").append(value == null ? "—" : formatNumber(value)).append("</td>");
                }
                html.append("</tr>");
            }
            html.append("</tbody></table></div>");
        }
    }

    private static void appendFields(StringBuilder html, List<AiExportRequest.Field> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        html.append("<div class=\"table-wrap\"><table class=\"fields\"><tbody>");
        for (AiExportRequest.Field field : fields) {
            html.append("<tr><th>").append(escape(field.getLabel())).append("</th><td>")
                    .append(cellHtml(field.cellValue())).append("</td></tr>");
        }
        html.append("</tbody></table></div>");
    }

    private static void appendList(StringBuilder html, List<AiExportRequest.Cell> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        html.append("<ul class=\"plain-list\">");
        for (AiExportRequest.Cell item : items) {
            html.append("<li>").append(cellHtml(item)).append("</li>");
        }
        html.append("</ul>");
    }

    private static void appendFiles(StringBuilder html, List<AiExportRequest.FileItem> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        html.append("<ul class=\"file-list\">");
        for (AiExportRequest.FileItem file : files) {
            String href = safeHref(file.getUrl());
            html.append("<li>");
            if (href == null) {
                html.append(escape(file.getName()));
            } else {
                html.append("<a href=\"").append(escape(href)).append("\" target=\"_blank\" rel=\"noopener\">")
                        .append(escape(file.getName())).append("</a>");
            }
            if (file.getSize() != null && !file.getSize().isBlank()) {
                html.append(" <span class=\"file-size\">").append(escape(file.getSize())).append("</span>");
            }
            html.append("</li>");
        }
        html.append("</ul>");
    }

    private static String cellHtml(AiExportRequest.Cell cell) {
        if (cell == null || cell.getText() == null || cell.getText().isBlank()) {
            return "<span class=\"empty\">—</span>";
        }
        String href = safeHref(cell.getHref());
        if ("link".equalsIgnoreCase(cell.getKind()) && href != null) {
            return "<a href=\"" + escape(href) + "\" target=\"_blank\" rel=\"noopener\">"
                    + escape(cell.getText()) + "</a>";
        }
        if ("code".equalsIgnoreCase(cell.getKind())) {
            return "<code>" + escape(cell.getText()) + "</code>";
        }
        if ("boolean".equalsIgnoreCase(cell.getKind())) {
            return "<span class=\"bool\">" + escape(cell.getText()) + "</span>";
        }
        return escape(cell.getText());
    }

    private static String safeHref(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String value = href.trim();
        return value.matches("^https?://.+") ? value : null;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String style() {
        return """
                * { box-sizing: border-box; }
                body { margin: 0; background: #f2f4f8; color: #1f2329;
                  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", -apple-system, sans-serif;
                  font-size: 14px; line-height: 1.7; }
                .report { max-width: 1080px; margin: 24px auto 48px; padding: 44px 52px 56px;
                  background: #fff; border-radius: 12px; box-shadow: 0 8px 28px rgba(31, 45, 61, .08); }
                .report-head { padding-bottom: 18px; border-bottom: 2px solid #eef1f7; }
                .report-title { margin: 0; font-size: 26px; line-height: 1.35; color: #1f2329; }
                .report-meta { margin: 10px 0 0; color: #8f959e; font-size: 12px; }
                main { padding-top: 8px; }
                h2, h3, h4, h5, h6 { margin: 24px 0 10px; color: #1f2329; line-height: 1.4; }
                h2 { font-size: 21px; } h3 { font-size: 18px; } h4 { font-size: 16px; }
                h5, h6 { font-size: 14px; }
                p { margin: 8px 0; }
                ul, ol { margin: 8px 0; padding-left: 22px; }
                li { margin: 3px 0; }
                a { color: #1664ff; text-decoration: none; }
                a:hover { text-decoration: underline; }
                blockquote { margin: 12px 0; padding: 8px 14px; border-left: 3px solid #d7e2ff;
                  background: #f7f9fd; color: #5b6579; }
                hr { margin: 20px 0; border: none; border-top: 1px solid #eef1f7; }
                .view-title { margin-top: 26px; padding-left: 10px; border-left: 4px solid #1664ff;
                  font-size: 16px; }
                .table-wrap { margin: 12px 0; overflow-x: auto; border: 1px solid #e8edf7; border-radius: 10px; }
                table { width: 100%; border-collapse: collapse; font-size: 13px; }
                th, td { padding: 9px 12px; border-bottom: 1px solid #eff2f8; text-align: left;
                  vertical-align: top; word-break: break-word; }
                thead th { position: sticky; top: 0; background: #f2f5fa; color: #5b6579;
                  font-weight: 600; white-space: nowrap; }
                tbody tr:nth-child(even) td { background: #fbfcfe; }
                tr:last-child td, tr:last-child th { border-bottom: none; }
                td code, p code { padding: 1px 5px; border-radius: 4px; background: #f4f5f7;
                  color: #4e5969; font-family: Consolas, Monaco, monospace; font-size: 12px; }
                .empty { color: #a9aeb8; }
                .bool { color: #00b42a; }
                .metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
                  gap: 12px; margin: 14px 0; }
                .metric { padding: 14px 16px; border: 1px solid #eef0f3; border-radius: 10px; background: #fafbfc; }
                .metric-label { color: #8f959e; font-size: 12px; }
                .metric-value { margin-top: 4px; font-size: 24px; font-weight: 600; color: #1f2329; }
                .metric-unit { margin-left: 4px; color: #8f959e; font-size: 12px; font-weight: 400; }
                .metric-trend { margin-top: 2px; color: #8f959e; font-size: 12px; }
                .metric-chips { display: flex; flex-wrap: wrap; gap: 8px 20px; margin: 10px 0;
                  padding: 10px 12px; background: #f7f9fd; border-radius: 10px; font-size: 13px; }
                .chip-label { margin-right: 4px; color: #8f959e; }
                .chart-wrap { margin: 14px 0; padding: 6px; border: 1px solid #eef1f7; border-radius: 10px; }
                svg.chart { display: block; width: 100%; height: auto; }
                .chart-data { max-height: 360px; overflow-y: auto; }
                .plain-list, .file-list { padding-left: 20px; }
                .file-size { margin-left: 6px; color: #a9aeb8; font-size: 12px; }
                .code { margin: 12px 0; padding: 12px 14px; background: #fafbfc; border: 1px solid #eef1f7;
                  border-radius: 8px; white-space: pre-wrap; word-break: break-word;
                  font-family: Consolas, Monaco, monospace; font-size: 12px; line-height: 1.6; }
                .text { white-space: normal; }
                .tip { margin: 8px 0; color: #a9aeb8; font-size: 12px; }
                .report-foot { margin-top: 32px; padding-top: 14px; border-top: 1px solid #eef1f7;
                  color: #a9aeb8; font-size: 12px; text-align: center; }
                @media print {
                  body { background: #fff; }
                  .report { max-width: none; margin: 0; padding: 0; box-shadow: none; border-radius: 0; }
                  .table-wrap { overflow: visible; }
                  .chart-data { max-height: none; }
                  thead th { position: static; }
                }
                """;
    }
}
