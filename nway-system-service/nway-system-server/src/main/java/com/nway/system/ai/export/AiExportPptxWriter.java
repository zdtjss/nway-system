package com.nway.system.ai.export;

import com.nway.system.ai.web.model.AiExportRequest;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PowerPoint 导出：封面 + 按内容自动分页的正文、表格、图表幻灯片。
 *
 * @author nway
 */
public final class AiExportPptxWriter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Color TEXT = new Color(0x1F, 0x23, 0x29);
    private static final Color MUTED = new Color(0x8F, 0x95, 0x9E);
    private static final Color ACCENT = new Color(0x16, 0x64, 0xFF);
    private static final Color HEADER_FILL = new Color(0xF2, 0xF5, 0xFA);
    private static final Color LINE = new Color(0xE8, 0xED, 0xF7);
    private static final int CONTENT_TOP = 92;
    private static final int CONTENT_BOTTOM = 494;
    private static final int MAX_TABLE_ROWS = 10;
    private static final int MAX_TEXT_CHARS = 1000;
    private static final int MAX_TEXT_LINES = 16;

    private AiExportPptxWriter() {
    }

    public static byte[] write(AiExportRequest request, String title, LocalDateTime generatedAt) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            slideShow.setPageSize(new Dimension(960, 540));
            Deck deck = new Deck(slideShow);
            addCover(deck, title, generatedAt);
            for (AiExportRequest.Block block : request.getBlocks()) {
                if (block == null) {
                    continue;
                }
                if ("view".equalsIgnoreCase(block.getType()) && block.getView() != null) {
                    addView(deck, block.getView());
                } else if (block.getText() != null && !block.getText().isBlank()) {
                    addMarkdown(deck, "分析说明", block.getText());
                }
            }
            if (deck.page == 1) {
                addTextSlides(deck, "分析说明", lines("本次分析未产生可导出的正文内容。", false, false, false));
            }
            slideShow.write(out);
            return out.toByteArray();
        }
    }

    // ==================== 封面与基础版式 ====================

    private static void addCover(Deck deck, String title, LocalDateTime generatedAt) {
        XSLFSlide slide = deck.nextSlide();
        XSLFAutoShape bar = slide.createAutoShape();
        bar.setShapeType(ShapeType.RECT);
        bar.setAnchor(new Rectangle2D.Double(60, 200, 150, 6));
        bar.setFillColor(ACCENT);
        bar.setLineColor(ACCENT);

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle2D.Double(60, 225, 840, 120));
        titleBox.setWordWrap(true);
        XSLFTextParagraph titleParagraph = titleBox.addNewTextParagraph();
        XSLFTextRun titleRun = titleParagraph.addNewTextRun();
        titleRun.setText(title);
        titleRun.setFontSize(34.0);
        titleRun.setBold(true);
        titleRun.setFontColor(TEXT);
        titleRun.setFontFamily("微软雅黑");

        XSLFTextBox metaBox = slide.createTextBox();
        metaBox.setAnchor(new Rectangle2D.Double(60, 360, 840, 60));
        XSLFTextParagraph metaParagraph = metaBox.addNewTextParagraph();
        XSLFTextRun metaRun = metaParagraph.addNewTextRun();
        metaRun.setText("AI 分析结果 · " + TIME.format(generatedAt));
        metaRun.setFontSize(14.0);
        metaRun.setFontColor(MUTED);
        metaRun.setFontFamily("微软雅黑");
    }

    private static XSLFSlide newContentSlide(Deck deck, String title) {
        XSLFSlide slide = deck.nextSlide();
        XSLFTextBox header = slide.createTextBox();
        header.setAnchor(new Rectangle2D.Double(40, 22, 880, 44));
        header.setWordWrap(true);
        XSLFTextParagraph paragraph = header.addNewTextParagraph();
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(clip(title, 46));
        run.setFontSize(20.0);
        run.setBold(true);
        run.setFontColor(TEXT);
        run.setFontFamily("微软雅黑");

        XSLFAutoShape line = slide.createAutoShape();
        line.setShapeType(ShapeType.RECT);
        line.setAnchor(new Rectangle2D.Double(40, 72, 880, 1.2));
        line.setFillColor(LINE);
        line.setLineColor(LINE);

        XSLFTextBox footer = slide.createTextBox();
        footer.setAnchor(new Rectangle2D.Double(40, 505, 880, 20));
        XSLFTextParagraph footerParagraph = footer.addNewTextParagraph();
        footerParagraph.setTextAlign(TextParagraph.TextAlign.RIGHT);
        XSLFTextRun footerRun = footerParagraph.addNewTextRun();
        footerRun.setText("AI 助手 · 第 " + deck.page + " 页");
        footerRun.setFontSize(9.0);
        footerRun.setFontColor(MUTED);
        return slide;
    }

    // ==================== Markdown ====================

    private static void addMarkdown(Deck deck, String title, String markdown) {
        List<AiExportMarkdown.MdBlock> blocks = AiExportMarkdown.parse(markdown);
        List<Line> buffer = new ArrayList<>();
        int chars = 0;
        for (AiExportMarkdown.MdBlock block : blocks) {
            switch (block.type()) {
                case TABLE -> {
                    flush(deck, title, buffer);
                    chars = 0;
                    if (block.table() != null && !block.table().isEmpty()) {
                        addTableSlides(deck, title, block.table().get(0), block.table().subList(1, block.table().size()),
                                null);
                    }
                }
                case CODE -> {
                    flush(deck, title, buffer);
                    chars = 0;
                    addCodeSlide(deck, title, block.text());
                }
                case HEADING -> {
                    if (chars > MAX_TEXT_CHARS - 60) {
                        flush(deck, title, buffer);
                        chars = 0;
                    }
                    for (String line : wrapLine(AiExportMarkdown.plain(block.text()))) {
                        buffer.add(new Line(line, true, false, false));
                        chars += 60;
                    }
                }
                case HR -> buffer.add(new Line("", false, false, false));
                case QUOTE -> {
                    for (String line : wrapLine(AiExportMarkdown.plain(block.text()))) {
                        buffer.add(new Line(line, false, false, false));
                        chars += 60;
                    }
                }
                default -> {
                    String text = AiExportMarkdown.plain(block.text());
                    boolean bullet = block.type() == AiExportMarkdown.BlockType.BULLET
                            || block.type() == AiExportMarkdown.BlockType.ORDERED;
                    String prefix = "";
                    if (block.type() == AiExportMarkdown.BlockType.BULLET) {
                        prefix = "• ";
                    } else if (block.type() == AiExportMarkdown.BlockType.ORDERED) {
                        prefix = block.marker() + " ";
                    }
                    for (String rawLine : text.split("\n", -1)) {
                        List<String> wrapped = wrapLine(rawLine);
                        for (int i = 0; i < wrapped.size(); i++) {
                            buffer.add(new Line((i == 0 ? prefix : "  ") + wrapped.get(i), false, bullet, false));
                        }
                        chars += rawLine.length();
                    }
                }
            }
            if (chars >= MAX_TEXT_CHARS || buffer.size() >= MAX_TEXT_LINES) {
                flush(deck, title, buffer);
                chars = 0;
            }
        }
        flush(deck, title, buffer);
    }

    private static void flush(Deck deck, String title, List<Line> buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        addTextSlides(deck, title, new ArrayList<>(buffer));
        buffer.clear();
    }

    private static void addTextSlides(Deck deck, String title, List<Line> lines) {
        int index = 0;
        while (index < lines.size()) {
            XSLFSlide slide = newContentSlide(deck, title);
            XSLFTextBox body = slide.createTextBox();
            body.setAnchor(new Rectangle2D.Double(48, CONTENT_TOP, 864, CONTENT_BOTTOM - CONTENT_TOP));
            body.setWordWrap(true);
            int used = 0;
            while (index < lines.size() && used < MAX_TEXT_LINES) {
                Line line = lines.get(index);
                XSLFTextParagraph paragraph = body.addNewTextParagraph();
                paragraph.setSpaceAfter(6.0);
                if (line.bullet()) {
                    paragraph.setIndentLevel(0);
                }
                XSLFTextRun run = paragraph.addNewTextRun();
                run.setText(line.text());
                run.setFontSize(line.heading() ? 16.0 : 13.0);
                run.setBold(line.heading());
                run.setFontColor(line.heading() ? TEXT : new Color(0x4E, 0x59, 0x69));
                run.setFontFamily("微软雅黑");
                index++;
                used++;
            }
        }
    }

    private static void addCodeSlide(Deck deck, String title, String code) {
        XSLFSlide slide = newContentSlide(deck, title);
        XSLFTextBox body = slide.createTextBox();
        body.setAnchor(new Rectangle2D.Double(48, CONTENT_TOP, 864, CONTENT_BOTTOM - CONTENT_TOP));
        body.setWordWrap(true);
        int count = 0;
        for (String line : (code == null ? "" : code).split("\n", -1)) {
            if (count++ >= MAX_TEXT_LINES) {
                break;
            }
            XSLFTextParagraph paragraph = body.addNewTextParagraph();
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(line);
            run.setFontSize(10.5);
            run.setFontColor(new Color(0x4E, 0x59, 0x69));
            run.setFontFamily("Consolas");
        }
    }

    // ==================== 视图 ====================

    private static void addView(Deck deck, AiExportRequest.View view) {
        String type = view.getType() == null ? "" : view.getType();
        String title = view.getTitle() == null || view.getTitle().isBlank() ? defaultViewTitle(type) : view.getTitle();
        switch (type) {
            case "table" -> {
                List<AiExportRequest.Column> columns = view.effectiveColumns();
                List<List<AiExportRequest.Cell>> rows = view.tableRows();
                if (columns.isEmpty()) {
                    addTextSlides(deck, title, lines("暂无数据", false, false, false));
                    return;
                }
                List<String> headers = new ArrayList<>();
                columns.forEach(column -> headers.add(column.getLabel() == null || column.getLabel().isBlank()
                        ? column.getKey() : column.getLabel()));
                List<List<String>> values = new ArrayList<>();
                for (List<AiExportRequest.Cell> row : rows) {
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < columns.size(); c++) {
                        AiExportRequest.Cell cell = c < row.size() ? row.get(c) : null;
                        cells.add(cell == null || cell.getText() == null || cell.getText().isBlank()
                                ? "—" : cell.getText());
                    }
                    values.add(cells);
                }
                addTableSlides(deck, title, headers, values, view.getSummary());
            }
            case "metric" -> {
                List<AiExportRequest.Metric> metrics = view.getMetrics() == null ? List.of() : view.getMetrics();
                List<String> headers = List.of("指标", "数值", "单位", "趋势");
                List<List<String>> values = new ArrayList<>();
                for (AiExportRequest.Metric metric : metrics) {
                    values.add(List.of(nvl(metric.getLabel()), nvl(metric.getValue()),
                            nvl(metric.getUnit()), nvl(metric.getTrend())));
                }
                addTableSlides(deck, title, metrics.isEmpty() ? List.of("指标") : headers, values, null);
            }
            case "chart" -> addChartSlide(deck, title, view.getChart());
            case "fields" -> {
                List<AiExportRequest.Field> fields = view.getFields() == null ? List.of() : view.getFields();
                List<String> headers = List.of("字段", "值");
                List<List<String>> values = new ArrayList<>();
                for (AiExportRequest.Field field : fields) {
                    AiExportRequest.Cell value = field.cellValue();
                    values.add(List.of(nvl(field.getLabel()), value == null ? "—" : nvl(value.getText())));
                }
                addTableSlides(deck, title, headers, values, null);
            }
            case "list" -> {
                List<AiExportRequest.Cell> items = view.listCells();
                List<Line> lines = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    lines.add(new Line((i + 1) + ". " + (items.get(i).getText() == null ? "—" : items.get(i).getText()),
                            false, false, false));
                }
                addTextSlides(deck, title, lines.isEmpty() ? lines("暂无清单数据", false, false, false) : lines);
            }
            case "file" -> {
                List<AiExportRequest.FileItem> files = view.getFiles() == null ? List.of() : view.getFiles();
                List<Line> lines = new ArrayList<>();
                for (AiExportRequest.FileItem file : files) {
                    String text = nvl(file.getName());
                    if (file.getSize() != null && !file.getSize().isBlank()) {
                        text += "（" + file.getSize() + "）";
                    }
                    if (file.getUrl() != null && !file.getUrl().isBlank()) {
                        text += "：" + file.getUrl();
                    }
                    lines.add(new Line("• " + text, false, false, false));
                }
                addTextSlides(deck, title, lines.isEmpty() ? lines("暂无文件", false, false, false) : lines);
            }
            case "text" -> addTextSlides(deck, title, lines(nvl(view.getText()), false, false, false));
            default -> addTextSlides(deck, title, lines(nvl(view.getMessage()), false, false, false));
        }
    }

    private static void addTableSlides(Deck deck, String title, List<String> headers, List<List<String>> rows,
                                       List<AiExportRequest.Metric> summary) {
        int columns = headers.size();
        if (columns == 0) {
            addTextSlides(deck, title, lines("暂无数据", false, false, false));
            return;
        }
        int offset = 0;
        boolean first = true;
        do {
            int from = offset;
            int to = Math.min(rows.size(), from + MAX_TABLE_ROWS);
            List<List<String>> chunk = rows.subList(from, to);
            XSLFSlide slide = newContentSlide(deck, first ? title : title + "（续）");
            double top = CONTENT_TOP;
            if (first && summary != null && !summary.isEmpty()) {
                XSLFTextBox summaryBox = slide.createTextBox();
                summaryBox.setAnchor(new Rectangle2D.Double(48, top, 864, 30));
                StringBuilder text = new StringBuilder();
                for (AiExportRequest.Metric metric : summary) {
                    if (text.length() > 0) {
                        text.append("    ");
                    }
                    text.append(metric.getLabel()).append("：").append(nvl(metric.getValue()));
                    if (metric.getUnit() != null && !metric.getUnit().isBlank()) {
                        text.append(metric.getUnit());
                    }
                }
                XSLFTextParagraph paragraph = summaryBox.addNewTextParagraph();
                XSLFTextRun run = paragraph.addNewTextRun();
                run.setText(text.toString());
                run.setFontSize(10.5);
                run.setFontColor(MUTED);
                run.setFontFamily("微软雅黑");
                top += 34;
            }
            double rowHeight = 26;
            double tableHeight = (chunk.size() + 1) * rowHeight;
            XSLFTable table = slide.createTable();
            table.setAnchor(new Rectangle2D.Double(48, top, 864, tableHeight));
            XSLFTableRow headerRow = table.addRow();
            headerRow.setHeight(rowHeight);
            for (int c = 0; c < columns; c++) {
                styleCell(headerRow.addCell(), nvl(headers.get(c)), true, HEADER_FILL);
            }
            double columnWidth = 864.0 / columns;
            for (int c = 0; c < columns; c++) {
                table.setColumnWidth(c, columnWidth);
            }
            for (List<String> row : chunk) {
                XSLFTableRow tableRow = table.addRow();
                tableRow.setHeight(rowHeight);
                for (int c = 0; c < columns; c++) {
                    styleCell(tableRow.addCell(), c < row.size() ? row.get(c) : "", false, Color.WHITE);
                }
            }
            first = false;
            offset = to;
        } while (offset < rows.size());
    }

    private static void addChartSlide(Deck deck, String title, AiExportRequest.Chart chart) {
        if (chart == null || chart.getSeries() == null || chart.getSeries().isEmpty()) {
            addTextSlides(deck, title, lines("暂无图表数据", false, false, false));
            return;
        }
        try {
            byte[] png = AiExportCharts.renderPng(chart);
            XSLFSlide slide = newContentSlide(deck, title);
            XSLFPictureData pictureData = deck.slideShow.addPicture(png, PictureData.PictureType.PNG);
            XSLFPictureShape picture = slide.createPicture(pictureData);
            double width = 760;
            double height = 427;
            picture.setAnchor(new Rectangle2D.Double((960 - width) / 2, CONTENT_TOP + 4, width, height));
        } catch (Exception e) {
            // 图片渲染失败时退化为数据表，保证内容完整
            List<String> headers = new ArrayList<>();
            headers.add("分类");
            chart.getSeries().forEach(item -> headers.add(nvl(item.getName())));
            List<List<String>> rows = new ArrayList<>();
            List<String> categories = chart.getCategories() == null ? List.of() : chart.getCategories();
            for (int i = 0; i < categories.size(); i++) {
                List<String> row = new ArrayList<>();
                row.add(categories.get(i));
                for (AiExportRequest.Series series : chart.getSeries()) {
                    List<Double> data = series.getData();
                    Double value = data != null && i < data.size() ? data.get(i) : null;
                    row.add(value == null ? "—" : String.valueOf(value));
                }
                rows.add(row);
            }
            addTableSlides(deck, title, headers, rows, null);
        }
    }

    private static void styleCell(XSLFTableCell cell, String text, boolean header, Color fill) {
        cell.setText(text == null ? "" : text);
        cell.setFillColor(fill);
        List<XSLFTextParagraph> paragraphs = cell.getTextParagraphs();
        if (paragraphs.isEmpty()) {
            return;
        }
        XSLFTextParagraph paragraph = paragraphs.get(0);
        paragraph.setSpaceAfter(0.0);
        for (XSLFTextRun run : paragraph.getTextRuns()) {
            run.setFontSize(10.0);
            run.setBold(header);
            run.setFontColor(header ? new Color(0x4E, 0x59, 0x69) : TEXT);
            run.setFontFamily("微软雅黑");
        }
    }

    private static List<Line> lines(String text, boolean heading, boolean bullet, boolean code) {
        List<Line> lines = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("\n", -1)) {
            lines.add(new Line(line, heading, bullet, code));
        }
        return lines;
    }

    /** 单行过长时按字符切分，避免自动换行后溢出文本框。 */
    private static List<String> wrapLine(String line) {
        List<String> parts = new ArrayList<>();
        String text = line == null ? "" : line;
        int index = 0;
        while (index < text.length()) {
            parts.add(text.substring(index, Math.min(text.length(), index + 170)));
            index += 170;
        }
        if (parts.isEmpty()) {
            parts.add("");
        }
        return parts;
    }

    private static String defaultViewTitle(String type) {
        return switch (type) {
            case "table" -> "数据明细";
            case "metric" -> "统计指标";
            case "chart" -> "数据图表";
            case "fields" -> "详情字段";
            case "list" -> "数据清单";
            case "file" -> "相关文件";
            default -> "分析结果";
        };
    }

    private static String nvl(String text) {
        return text == null ? "" : text;
    }

    private static String clip(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "…" : text;
    }

    private record Line(String text, boolean heading, boolean bullet, boolean code) {
    }

    private static final class Deck {

        private final XMLSlideShow slideShow;

        private int page;

        private Deck(XMLSlideShow slideShow) {
            this.slideShow = slideShow;
        }

        private XSLFSlide nextSlide() {
            page++;
            return slideShow.createSlide();
        }
    }
}
