package com.nway.system.ai.export;

import com.nway.system.ai.web.model.AiExportRequest;
import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 导出：标题、Markdown 正文、表格、指标与图表图片统一排版为 A4 报告。
 *
 * @author nway
 */
public final class AiExportDocxWriter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String BODY_FONT = "微软雅黑";
    private static final String MONO_FONT = "Consolas";
    private static final String TITLE_COLOR = "1F2329";
    private static final String MUTED_COLOR = "8F959E";
    private static final String LINK_COLOR = "1664FF";
    private static final double CHART_WIDTH = 500;
    private static final double CHART_HEIGHT = 281;

    private AiExportDocxWriter() {
    }

    public static byte[] write(AiExportRequest request, String title, LocalDateTime generatedAt) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addTitle(document, title, generatedAt);
            boolean first = true;
            for (AiExportRequest.Block block : request.getBlocks()) {
                if (block == null) {
                    continue;
                }
                if ("view".equalsIgnoreCase(block.getType()) && block.getView() != null) {
                    if (!first) {
                        XWPFParagraph pageBreak = document.createParagraph();
                        pageBreak.setPageBreak(true);
                    }
                    addView(document, block.getView());
                } else if (block.getText() != null && !block.getText().isBlank()) {
                    addMarkdown(document, block.getText());
                }
                first = false;
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    private static void addTitle(XWPFDocument document, String title, LocalDateTime generatedAt) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(21);
        run.setColor(TITLE_COLOR);
        setFont(run, BODY_FONT);

        XWPFParagraph meta = document.createParagraph();
        meta.setSpacingAfter(200);
        XWPFRun metaRun = meta.createRun();
        metaRun.setText("由 AI 助手生成 · " + TIME.format(generatedAt));
        metaRun.setFontSize(9.5);
        metaRun.setColor(MUTED_COLOR);
        setFont(metaRun, BODY_FONT);
    }

    // ==================== Markdown ====================

    private static void addMarkdown(XWPFDocument document, String markdown) {
        for (AiExportMarkdown.MdBlock block : AiExportMarkdown.parse(markdown)) {
            switch (block.type()) {
                case HEADING -> addHeading(document, block.level(), block.text());
                case PARAGRAPH -> {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setSpacingAfter(80);
                    addInlineLines(paragraph, block.text(), 10.5, TITLE_COLOR);
                }
                case BULLET -> {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setIndentationLeft(320);
                    paragraph.setSpacingAfter(40);
                    XWPFRun marker = paragraph.createRun();
                    marker.setText("• ");
                    marker.setFontSize(10.5);
                    setFont(marker, BODY_FONT);
                    addInlineLines(paragraph, block.text(), 10.5, TITLE_COLOR);
                }
                case ORDERED -> {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setIndentationLeft(320);
                    paragraph.setSpacingAfter(40);
                    XWPFRun marker = paragraph.createRun();
                    marker.setText((block.marker() == null ? "1." : block.marker()) + " ");
                    marker.setFontSize(10.5);
                    setFont(marker, BODY_FONT);
                    addInlineLines(paragraph, block.text(), 10.5, TITLE_COLOR);
                }
                case CODE -> addCode(document, block.text());
                case QUOTE -> {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setIndentationLeft(300);
                    paragraph.setSpacingAfter(80);
                    XWPFRun run = paragraph.createRun();
                    run.setText(AiExportMarkdown.plain(block.text()));
                    run.setItalic(true);
                    run.setFontSize(10);
                    run.setColor("5B6579");
                    setFont(run, BODY_FONT);
                }
                case TABLE -> addMarkdownTable(document, block.table());
                case HR -> {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setSpacingAfter(80);
                    XWPFRun run = paragraph.createRun();
                    run.setText("────────────────────────────");
                    run.setFontSize(8);
                    run.setColor("D9DDE5");
                }
            }
        }
    }

    private static void addHeading(XWPFDocument document, int level, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(160);
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setText(AiExportMarkdown.plain(text));
        run.setBold(true);
        run.setFontSize(Math.max(11.5, 16 - (level - 1) * 1.5));
        run.setColor(TITLE_COLOR);
        setFont(run, BODY_FONT);
    }

    private static void addCode(XWPFDocument document, String code) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(120);
        paragraph.setSpacingBefore(80);
        paragraph.setSpacingAfter(120);
        String[] lines = code == null ? new String[0] : code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i]);
            run.setFontSize(9);
            run.setColor("4E5969");
            setFont(run, MONO_FONT);
        }
    }

    private static void addInlineLines(XWPFParagraph paragraph, String text, double fontSize, String color) {
        String[] lines = text == null ? new String[0] : text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                XWPFRun br = paragraph.createRun();
                br.addBreak();
            }
            addInlineRuns(paragraph, lines[i], fontSize, color);
        }
    }

    private static void addInlineRuns(XWPFParagraph paragraph, String text, double fontSize, String color) {
        for (AiExportMarkdown.Inline inline : AiExportMarkdown.inline(text)) {
            String content = inline.text();
            if (content == null || content.isEmpty()) {
                continue;
            }
            switch (inline.type()) {
                case LINK -> {
                    String label = content;
                    String href = inline.href();
                    if (href != null && !href.isBlank() && !href.equals(content)) {
                        label = content + "（" + href + "）";
                    }
                    XWPFRun run = paragraph.createRun();
                    run.setText(label);
                    run.setFontSize(fontSize);
                    run.setColor(LINK_COLOR);
                    run.setUnderline(UnderlinePatterns.SINGLE);
                    setFont(run, BODY_FONT);
                }
                case CODE -> {
                    XWPFRun run = paragraph.createRun();
                    run.setText(content);
                    run.setFontSize(Math.max(8.5, fontSize - 0.5));
                    run.setColor("4E5969");
                    setFont(run, MONO_FONT);
                }
                case BOLD -> {
                    XWPFRun run = paragraph.createRun();
                    run.setText(content);
                    run.setBold(true);
                    run.setFontSize(fontSize);
                    run.setColor(color);
                    setFont(run, BODY_FONT);
                }
                case ITALIC -> {
                    XWPFRun run = paragraph.createRun();
                    run.setText(content);
                    run.setItalic(true);
                    run.setFontSize(fontSize);
                    run.setColor(color);
                    setFont(run, BODY_FONT);
                }
                case STRIKE -> {
                    XWPFRun run = paragraph.createRun();
                    run.setText(content);
                    run.setStrikeThrough(true);
                    run.setFontSize(fontSize);
                    run.setColor(color);
                    setFont(run, BODY_FONT);
                }
                default -> {
                    XWPFRun run = paragraph.createRun();
                    run.setText(content);
                    run.setFontSize(fontSize);
                    run.setColor(color);
                    setFont(run, BODY_FONT);
                }
            }
        }
    }

    private static void addMarkdownTable(XWPFDocument document, List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columns == 0) {
            return;
        }
        XWPFTable table = document.createTable(rows.size(), columns);
        for (int r = 0; r < rows.size(); r++) {
            List<String> values = rows.get(r);
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < columns; c++) {
                String value = c < values.size() ? values.get(c) : "";
                writeCell(row.getCell(c), AiExportMarkdown.plain(value), r == 0, "F2F5FA");
            }
        }
        XWPFParagraph spacing = document.createParagraph();
        spacing.setSpacingAfter(80);
    }

    // ==================== 结构化视图 ====================

    private static void addView(XWPFDocument document, AiExportRequest.View view) {
        if (view.getTitle() != null && !view.getTitle().isBlank()) {
            addViewTitle(document, view.getTitle());
        }
        String type = view.getType() == null ? "" : view.getType();
        switch (type) {
            case "table" -> addTableView(document, view);
            case "metric" -> addMetrics(document, view.getMetrics());
            case "chart" -> addChartView(document, view);
            case "fields" -> addFields(document, view.getFields());
            case "list" -> addList(document, view.listCells());
            case "file" -> addFiles(document, view.getFiles());
            case "text" -> addInlineLines(document.createParagraph(), view.getText(), 10.5, TITLE_COLOR);
            default -> addNote(document, view.getMessage() == null || view.getMessage().isBlank()
                    ? "接口未返回数据" : view.getMessage());
        }
    }

    private static void addViewTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(80);
        paragraph.setSpacingAfter(100);
        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(13);
        run.setColor(TITLE_COLOR);
        setFont(run, BODY_FONT);
    }

    private static void addTableView(XWPFDocument document, AiExportRequest.View view) {
        if (view.getSummary() != null && !view.getSummary().isEmpty()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingAfter(80);
            for (int i = 0; i < view.getSummary().size(); i++) {
                AiExportRequest.Metric metric = view.getSummary().get(i);
                if (i > 0) {
                    XWPFRun sep = paragraph.createRun();
                    sep.setText("    ");
                }
                XWPFRun label = paragraph.createRun();
                label.setText(metric.getLabel() + "：");
                label.setFontSize(9.5);
                label.setColor(MUTED_COLOR);
                setFont(label, BODY_FONT);
                XWPFRun value = paragraph.createRun();
                value.setText(metricValue(metric));
                value.setBold(true);
                value.setFontSize(9.5);
                value.setColor(TITLE_COLOR);
                setFont(value, BODY_FONT);
            }
        }
        List<AiExportRequest.Column> columns = view.effectiveColumns();
        List<List<AiExportRequest.Cell>> rows = view.tableRows();
        if (columns.isEmpty()) {
            addNote(document, "暂无数据");
            return;
        }
        XWPFTable table = document.createTable(rows.size() + 1, columns.size());
        XWPFTableRow header = table.getRow(0);
        for (int c = 0; c < columns.size(); c++) {
            AiExportRequest.Column column = columns.get(c);
            String label = column.getLabel() == null || column.getLabel().isBlank()
                    ? column.getKey() : column.getLabel();
            writeCell(header.getCell(c), label, true, "F2F5FA");
        }
        for (int r = 0; r < rows.size(); r++) {
            List<AiExportRequest.Cell> values = rows.get(r);
            XWPFTableRow row = table.getRow(r + 1);
            for (int c = 0; c < columns.size(); c++) {
                AiExportRequest.Cell cell = c < values.size() ? values.get(c) : null;
                writeCell(row.getCell(c), cellText(cell), false, null);
            }
        }
        if (view.getTotal() != null && view.getTotal() > rows.size()) {
            addNote(document, "共 " + view.getTotal() + " 条，页面展示前 " + rows.size() + " 条");
        }
        XWPFParagraph spacing = document.createParagraph();
        spacing.setSpacingAfter(80);
    }

    private static void addMetrics(XWPFDocument document, List<AiExportRequest.Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            addNote(document, "暂无统计数据");
            return;
        }
        XWPFTable table = document.createTable(metrics.size() + 1, 4);
        String[] headers = {"指标", "数值", "单位", "趋势"};
        for (int c = 0; c < headers.length; c++) {
            writeCell(table.getRow(0).getCell(c), headers[c], true, "F2F5FA");
        }
        for (int r = 0; r < metrics.size(); r++) {
            AiExportRequest.Metric metric = metrics.get(r);
            XWPFTableRow row = table.getRow(r + 1);
            writeCell(row.getCell(0), metric.getLabel(), false, null);
            writeCell(row.getCell(1), metric.getValue(), false, null);
            writeCell(row.getCell(2), metric.getUnit(), false, null);
            writeCell(row.getCell(3), metric.getTrend(), false, null);
        }
        XWPFParagraph spacing = document.createParagraph();
        spacing.setSpacingAfter(80);
    }

    private static void addChartView(XWPFDocument document, AiExportRequest.View view) {
        AiExportRequest.Chart chart = view.getChart();
        if (chart == null || chart.getSeries() == null || chart.getSeries().isEmpty()) {
            addNote(document, "暂无图表数据");
            return;
        }
        try {
            byte[] png = AiExportCharts.renderPng(chart);
            XWPFParagraph imageParagraph = document.createParagraph();
            imageParagraph.setAlignment(ParagraphAlignment.CENTER);
            imageParagraph.setSpacingAfter(80);
            XWPFRun run = imageParagraph.createRun();
            run.addPicture(new ByteArrayInputStream(png), PictureType.PNG,
                    "chart.png", Units.toEMU(CHART_WIDTH), Units.toEMU(CHART_HEIGHT));
        } catch (Exception e) {
            // 图片渲染失败时仍保留下方数据表，保证数字不丢失
            addNote(document, "图表图片生成失败，已保留图表数据表");
        }
        List<String> categories = chart.getCategories() == null ? List.of() : chart.getCategories();
        if (!categories.isEmpty()) {
            List<String> headers = new ArrayList<>();
            headers.add("分类");
            chart.getSeries().forEach(item -> headers.add(item.getName()));
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < categories.size(); i++) {
                List<String> row = new ArrayList<>();
                row.add(categories.get(i));
                for (AiExportRequest.Series series : chart.getSeries()) {
                    List<Double> data = series.getData();
                    Double value = data != null && i < data.size() ? data.get(i) : null;
                    row.add(value == null ? "—" : formatNumber(value));
                }
                rows.add(row);
            }
            XWPFTable table = document.createTable(rows.size() + 1, headers.size());
            for (int c = 0; c < headers.size(); c++) {
                writeCell(table.getRow(0).getCell(c), headers.get(c), true, "F2F5FA");
            }
            for (int r = 0; r < rows.size(); r++) {
                for (int c = 0; c < headers.size(); c++) {
                    writeCell(table.getRow(r + 1).getCell(c), rows.get(r).get(c), false, null);
                }
            }
        }
        XWPFParagraph spacing = document.createParagraph();
        spacing.setSpacingAfter(80);
    }

    private static void addFields(XWPFDocument document, List<AiExportRequest.Field> fields) {
        if (fields == null || fields.isEmpty()) {
            addNote(document, "暂无字段数据");
            return;
        }
        XWPFTable table = document.createTable(fields.size() + 1, 2);
        writeCell(table.getRow(0).getCell(0), "字段", true, "F2F5FA");
        writeCell(table.getRow(0).getCell(1), "值", true, "F2F5FA");
        for (int r = 0; r < fields.size(); r++) {
            AiExportRequest.Field field = fields.get(r);
            writeCell(table.getRow(r + 1).getCell(0), field.getLabel(), false, "F7F9FD");
            writeCell(table.getRow(r + 1).getCell(1), cellText(field.cellValue()), false, null);
        }
        XWPFParagraph spacing = document.createParagraph();
        spacing.setSpacingAfter(80);
    }

    private static void addList(XWPFDocument document, List<AiExportRequest.Cell> items) {
        if (items == null || items.isEmpty()) {
            addNote(document, "暂无清单数据");
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(320);
            paragraph.setSpacingAfter(40);
            XWPFRun marker = paragraph.createRun();
            marker.setText((i + 1) + ". ");
            marker.setFontSize(10.5);
            marker.setColor(MUTED_COLOR);
            setFont(marker, BODY_FONT);
            addInlineLines(paragraph, cellText(items.get(i)), 10.5, TITLE_COLOR);
        }
    }

    private static void addFiles(XWPFDocument document, List<AiExportRequest.FileItem> files) {
        if (files == null || files.isEmpty()) {
            addNote(document, "暂无文件");
            return;
        }
        for (AiExportRequest.FileItem file : files) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(320);
            paragraph.setSpacingAfter(40);
            XWPFRun run = paragraph.createRun();
            String text = file.getName() == null ? "文件" : file.getName();
            if (file.getSize() != null && !file.getSize().isBlank()) {
                text += "（" + file.getSize() + "）";
            }
            if (file.getUrl() != null && !file.getUrl().isBlank()) {
                text += "：" + file.getUrl();
            }
            run.setText("• " + text);
            run.setFontSize(9.5);
            run.setColor(TITLE_COLOR);
            setFont(run, BODY_FONT);
        }
    }

    private static void addNote(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(9.5);
        run.setColor(MUTED_COLOR);
        setFont(run, BODY_FONT);
    }

    // ==================== 表格工具 ====================

    private static void writeCell(XWPFTableCell cell, String text, boolean bold, String fill) {
        if (cell == null) {
            return;
        }
        if (fill != null) {
            try {
                cell.setColor(fill);
            } catch (Exception ignore) {
                // 部分 Word 视图不支持单元格底色，忽略即可
            }
        }
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBefore(0);
        XWPFRun run = paragraph.createRun();
        run.setText(text == null || text.isBlank() ? "—" : text);
        run.setFontSize(9);
        run.setBold(bold);
        run.setColor(bold ? "4E5969" : TITLE_COLOR);
        setFont(run, BODY_FONT);
    }

    private static String cellText(AiExportRequest.Cell cell) {
        if (cell == null || cell.getText() == null || cell.getText().isBlank()) {
            return "—";
        }
        return cell.getText();
    }

    private static String metricValue(AiExportRequest.Metric metric) {
        StringBuilder value = new StringBuilder(metric.getValue() == null ? "" : metric.getValue());
        if (metric.getUnit() != null && !metric.getUnit().isBlank()) {
            value.append(metric.getUnit());
        }
        if (metric.getTrend() != null && !metric.getTrend().isBlank()) {
            value.append("（").append(metric.getTrend()).append("）");
        }
        return value.toString();
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static void setFont(XWPFRun run, String family) {
        try {
            run.setFontFamily(family);
            run.setFontFamily(family, XWPFRun.FontCharRange.eastAsia);
        } catch (Exception ignore) {
            // 字体设置失败不影响文本内容
        }
    }
}
