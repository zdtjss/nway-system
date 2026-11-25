package com.nway.system.ai.export;

import com.nway.system.ai.web.model.AiExportRequest;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Excel 导出：概览页承载正文与统计指标，每个表格 / 图表独立 Sheet，数字保留可计算类型。
 *
 * @author nway
 */
public final class AiExportXlsxWriter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_CELL_CHARS = 32_000;
    private static final int MAX_SHEET_NAME = 31;

    private AiExportXlsxWriter() {
    }

    public static byte[] write(AiExportRequest request, String title, LocalDateTime generatedAt) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet overview = workbook.createSheet("分析概览");
            overview.setColumnWidth(0, 22 * 256);
            overview.setColumnWidth(1, 30 * 256);
            overview.setColumnWidth(2, 12 * 256);
            overview.setColumnWidth(3, 18 * 256);
            overview.setColumnWidth(4, 60 * 256);
            int rowIndex = 0;
            rowIndex = writeTitle(overview, rowIndex, title, generatedAt, styles);
            Set<String> usedNames = new HashSet<>();
            usedNames.add("分析概览");
            for (AiExportRequest.Block block : request.getBlocks()) {
                if (block == null) {
                    continue;
                }
                if ("view".equalsIgnoreCase(block.getType()) && block.getView() != null) {
                    rowIndex = writeView(workbook, overview, rowIndex, block.getView(), usedNames, styles, generatedAt);
                } else if (block.getText() != null && !block.getText().isBlank()) {
                    rowIndex = writeMarkdownText(overview, rowIndex, block.getText(), styles);
                }
            }
            if (rowIndex <= 3) {
                Row row = overview.createRow(rowIndex + 1);
                Cell cell = row.createCell(0);
                cell.setCellValue("本次分析未产生可导出的内容。");
                cell.setCellStyle(styles.muted);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static int writeTitle(Sheet sheet, int rowIndex, String title, LocalDateTime generatedAt, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(28);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 4));

        Row meta = sheet.createRow(rowIndex + 1);
        Cell metaCell = meta.createCell(0);
        metaCell.setCellValue("由 AI 助手生成 · " + TIME.format(generatedAt));
        metaCell.setCellStyle(styles.muted);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex + 1, rowIndex + 1, 0, 4));
        return rowIndex + 2;
    }

    private static int writeMarkdownText(Sheet sheet, int rowIndex, String markdown, Styles styles) {
        int index = rowIndex;
        for (AiExportMarkdown.MdBlock block : AiExportMarkdown.parse(markdown)) {
            switch (block.type()) {
                case HEADING -> {
                    index++;
                    Row row = sheet.createRow(index++);
                    Cell cell = row.createCell(0);
                    cell.setCellValue(AiExportMarkdown.plain(block.text()));
                    cell.setCellStyle(styles.section);
                }
                case TABLE -> {
                    if (block.table() != null && !block.table().isEmpty()) {
                        index++;
                        index = writePlainTable(sheet, index, block.table().get(0),
                                block.table().subList(1, block.table().size()), styles);
                    }
                }
                case CODE -> {
                    for (String line : (block.text() == null ? "" : block.text()).split("\n", -1)) {
                        Row row = sheet.createRow(index++);
                        Cell cell = row.createCell(0);
                        cell.setCellValue(cap(line));
                        cell.setCellStyle(styles.code);
                    }
                }
                default -> {
                    String text = AiExportMarkdown.plain(block.text());
                    for (String line : text.split("\n", -1)) {
                        Row row = sheet.createRow(index++);
                        Cell cell = row.createCell(0);
                        cell.setCellValue(cap(line));
                        cell.setCellStyle(styles.body);
                        sheet.addMergedRegion(new CellRangeAddress(index - 1, index - 1, 0, 4));
                    }
                }
            }
        }
        return index;
    }

    private static int writeView(XSSFWorkbook workbook, Sheet overview, int rowIndex, AiExportRequest.View view,
                                 Set<String> usedNames, Styles styles, LocalDateTime generatedAt) {
        String type = view.getType() == null ? "" : view.getType();
        if ("metric".equals(type)) {
            return writeMetricOverview(overview, rowIndex, view, styles);
        }
        String title = view.getTitle() == null || view.getTitle().isBlank()
                ? defaultViewTitle(type) : view.getTitle();
        Sheet sheet = workbook.createSheet(sheetName(title, usedNames));
        int index = writeTitle(sheet, 0, title, generatedAt, styles) + 1;
        switch (type) {
            case "table" -> index = writeTableSheet(sheet, index, view, styles);
            case "chart" -> index = writeChartSheet(workbook, sheet, index, view, styles);
            case "fields" -> index = writeFieldsSheet(sheet, index, view, styles);
            case "list" -> index = writeListSheet(sheet, index, view, styles);
            case "file" -> index = writeFilesSheet(sheet, index, view, styles);
            case "text" -> {
                Row row = sheet.createRow(index++);
                Cell cell = row.createCell(0);
                cell.setCellValue(cap(view.getText()));
                cell.setCellStyle(styles.body);
                sheet.setColumnWidth(0, 90 * 256);
            }
            default -> {
                Row row = sheet.createRow(index++);
                Cell cell = row.createCell(0);
                cell.setCellValue(view.getMessage() == null || view.getMessage().isBlank()
                        ? "接口未返回数据" : view.getMessage());
                cell.setCellStyle(styles.muted);
            }
        }
        return rowIndex;
    }

    private static int writeMetricOverview(Sheet sheet, int rowIndex, AiExportRequest.View view, Styles styles) {
        List<AiExportRequest.Metric> metrics = view.getMetrics() == null ? List.of() : view.getMetrics();
        int index = rowIndex + 1;
        if (view.getTitle() != null && !view.getTitle().isBlank()) {
            Row titleRow = sheet.createRow(index++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(view.getTitle());
            titleCell.setCellStyle(styles.section);
        }
        Row header = sheet.createRow(index++);
        String[] headers = {"指标", "数值", "单位", "趋势"};
        for (int c = 0; c < headers.length; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(styles.header);
        }
        for (AiExportRequest.Metric metric : metrics) {
            Row row = sheet.createRow(index++);
            writeText(row.createCell(0), metric.getLabel(), styles.body);
            writeText(row.createCell(1), metric.getValue(), styles.body);
            writeText(row.createCell(2), metric.getUnit(), styles.body);
            writeText(row.createCell(3), metric.getTrend(), styles.body);
        }
        return index;
    }

    private static int writeTableSheet(Sheet sheet, int rowIndex, AiExportRequest.View view, Styles styles) {
        int index = rowIndex;
        List<AiExportRequest.Column> columns = view.effectiveColumns();
        List<List<AiExportRequest.Cell>> rows = view.tableRows();
        if (columns.isEmpty()) {
            Row row = sheet.createRow(index++);
            Cell cell = row.createCell(0);
            cell.setCellValue("暂无数据");
            cell.setCellStyle(styles.muted);
            return index;
        }
        if (view.getSummary() != null && !view.getSummary().isEmpty()) {
            Row summaryHeader = sheet.createRow(index++);
            Cell summaryCell = summaryHeader.createCell(0);
            summaryCell.setCellValue("统计摘要");
            summaryCell.setCellStyle(styles.section);
            for (AiExportRequest.Metric metric : view.getSummary()) {
                Row row = sheet.createRow(index++);
                writeText(row.createCell(0), metric.getLabel(), styles.body);
                writeText(row.createCell(1), metricValue(metric), styles.body);
            }
            index++;
        }
        Row header = sheet.createRow(index++);
        for (int c = 0; c < columns.size(); c++) {
            AiExportRequest.Column column = columns.get(c);
            Cell cell = header.createCell(c);
            cell.setCellValue(column.getLabel() == null || column.getLabel().isBlank()
                    ? column.getKey() : column.getLabel());
            cell.setCellStyle(styles.header);
        }
        sheet.createFreezePane(0, index);
        int[] widths = new int[columns.size()];
        for (List<AiExportRequest.Cell> values : rows) {
            Row row = sheet.createRow(index++);
            for (int c = 0; c < columns.size(); c++) {
                AiExportRequest.Cell value = c < values.size() ? values.get(c) : null;
                writeValueCell(row.createCell(c), value, styles);
                widths[c] = Math.max(widths[c], displayLength(value == null ? "" : value.getText()));
            }
        }
        applyWidths(sheet, widths);
        if (view.getTotal() != null && view.getTotal() > rows.size()) {
            Row note = sheet.createRow(index);
            Cell cell = note.createCell(0);
            cell.setCellValue("共 " + view.getTotal() + " 条，页面展示前 " + rows.size() + " 条");
            cell.setCellStyle(styles.muted);
        }
        return index;
    }

    private static int writeChartSheet(XSSFWorkbook workbook, Sheet sheet, int rowIndex, AiExportRequest.View view,
                                       Styles styles) {
        AiExportRequest.Chart chart = view.getChart();
        if (chart == null || chart.getSeries() == null || chart.getSeries().isEmpty()) {
            Row row = sheet.createRow(rowIndex);
            Cell cell = row.createCell(0);
            cell.setCellValue("暂无图表数据");
            cell.setCellStyle(styles.muted);
            return rowIndex + 1;
        }
        try {
            byte[] png = AiExportCharts.renderPng(chart);
            int pictureIndex = workbook.addPicture(png, Workbook.PICTURE_TYPE_PNG);
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, 0, rowIndex, 10, rowIndex + 22);
            drawing.createPicture(anchor, pictureIndex);
        } catch (Exception e) {
            Row note = sheet.createRow(rowIndex);
            Cell noteCell = note.createCell(0);
            noteCell.setCellValue("图表图片生成失败，已保留下方数据表");
            noteCell.setCellStyle(styles.muted);
        }
        int index = rowIndex + 24;
        for (int c = 0; c < 10; c++) {
            sheet.setColumnWidth(c, 12 * 256);
        }
        // 图表数据表：保留原始数值，便于二次核对与计算
        List<String> categories = chart.getCategories() == null ? List.of() : chart.getCategories();
        Row header = sheet.createRow(index++);
        Cell categoryHeader = header.createCell(0);
        categoryHeader.setCellValue("分类");
        categoryHeader.setCellStyle(styles.header);
        for (int c = 0; c < chart.getSeries().size(); c++) {
            Cell cell = header.createCell(c + 1);
            cell.setCellValue(chart.getSeries().get(c).getName() == null
                    ? "数值" : chart.getSeries().get(c).getName());
            cell.setCellStyle(styles.header);
        }
        for (int i = 0; i < categories.size(); i++) {
            Row row = sheet.createRow(index++);
            writeText(row.createCell(0), categories.get(i), styles.body);
            for (int c = 0; c < chart.getSeries().size(); c++) {
                List<Double> data = chart.getSeries().get(c).getData();
                Double value = data != null && i < data.size() ? data.get(i) : null;
                Cell cell = row.createCell(c + 1);
                if (value == null) {
                    cell.setBlank();
                } else {
                    cell.setCellValue(value);
                    cell.setCellStyle(styles.number);
                }
            }
        }
        return index;
    }

    private static int writeFieldsSheet(Sheet sheet, int rowIndex, AiExportRequest.View view, Styles styles) {
        int index = rowIndex;
        Row header = sheet.createRow(index++);
        Cell nameHeader = header.createCell(0);
        nameHeader.setCellValue("字段");
        nameHeader.setCellStyle(styles.header);
        Cell valueHeader = header.createCell(1);
        valueHeader.setCellValue("值");
        valueHeader.setCellStyle(styles.header);
        List<AiExportRequest.Field> fields = view.getFields() == null ? List.of() : view.getFields();
        int width = 0;
        for (AiExportRequest.Field field : fields) {
            Row row = sheet.createRow(index++);
            writeText(row.createCell(0), field.getLabel(), styles.body);
            AiExportRequest.Cell value = field.cellValue();
            writeValueCell(row.createCell(1), value, styles);
            width = Math.max(width, displayLength(value == null ? "" : value.getText()));
        }
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, Math.min(80, Math.max(20, width)) * 256);
        return index;
    }

    private static int writeListSheet(Sheet sheet, int rowIndex, AiExportRequest.View view, Styles styles) {
        int index = rowIndex;
        Row header = sheet.createRow(index++);
        Cell cell = header.createCell(0);
        cell.setCellValue("序号");
        cell.setCellStyle(styles.header);
        Cell valueHeader = header.createCell(1);
        valueHeader.setCellValue("内容");
        valueHeader.setCellStyle(styles.header);
        List<AiExportRequest.Cell> items = view.listCells();
        int width = 0;
        for (int i = 0; i < items.size(); i++) {
            Row row = sheet.createRow(index++);
            Cell indexCell = row.createCell(0);
            indexCell.setCellValue(i + 1);
            indexCell.setCellStyle(styles.number);
            writeValueCell(row.createCell(1), items.get(i), styles);
            width = Math.max(width, displayLength(items.get(i).getText()));
        }
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, Math.min(80, Math.max(20, width)) * 256);
        return index;
    }

    private static int writeFilesSheet(Sheet sheet, int rowIndex, AiExportRequest.View view, Styles styles) {
        int index = rowIndex;
        Row header = sheet.createRow(index++);
        String[] headers = {"文件", "大小", "地址"};
        for (int c = 0; c < headers.length; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(styles.header);
        }
        List<AiExportRequest.FileItem> files = view.getFiles() == null ? List.of() : view.getFiles();
        for (AiExportRequest.FileItem file : files) {
            Row row = sheet.createRow(index++);
            writeText(row.createCell(0), file.getName(), styles.body);
            writeText(row.createCell(1), file.getSize(), styles.body);
            String url = file.getUrl();
            Cell urlCell = row.createCell(2);
            if (url != null && url.matches("^https?://.+")) {
                urlCell.setCellValue(url);
                Hyperlink hyperlink = sheet.getWorkbook().getCreationHelper()
                        .createHyperlink(HyperlinkType.URL);
                hyperlink.setAddress(url);
                urlCell.setHyperlink(hyperlink);
                urlCell.setCellStyle(styles.link);
            } else {
                writeText(urlCell, url, styles.body);
            }
        }
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 60 * 256);
        return index;
    }

    private static int writePlainTable(Sheet sheet, int rowIndex, List<String> headers, List<List<String>> rows,
                                       Styles styles) {
        int index = rowIndex;
        Row header = sheet.createRow(index++);
        for (int c = 0; c < headers.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(AiExportMarkdown.plain(headers.get(c)));
            cell.setCellStyle(styles.header);
        }
        int[] widths = new int[headers.size()];
        for (int c = 0; c < headers.size(); c++) {
            widths[c] = displayLength(headers.get(c));
        }
        for (List<String> values : rows) {
            Row row = sheet.createRow(index++);
            for (int c = 0; c < headers.size(); c++) {
                String text = c < values.size() ? values.get(c) : "";
                writeText(row.createCell(c), AiExportMarkdown.plain(text), styles.body);
                widths[c] = Math.max(widths[c], displayLength(text));
            }
        }
        applyWidths(sheet, widths);
        return index;
    }

    private static void writeValueCell(Cell cell, AiExportRequest.Cell value, Styles styles) {
        if (value == null || value.getText() == null || value.getText().isBlank()) {
            cell.setCellValue("—");
            cell.setCellStyle(styles.muted);
            return;
        }
        if ("number".equalsIgnoreCase(value.getKind())) {
            try {
                cell.setCellValue(Double.parseDouble(value.getText().replace(",", "")));
                cell.setCellStyle(styles.number);
                return;
            } catch (NumberFormatException ignore) {
                // 非标准数字按文本写入，避免丢内容
            }
        }
        if ("link".equalsIgnoreCase(value.getKind()) && value.getHref() != null
                && value.getHref().matches("^https?://.+")) {
            cell.setCellValue(value.getText());
            Hyperlink hyperlink = cell.getSheet().getWorkbook().getCreationHelper()
                    .createHyperlink(HyperlinkType.URL);
            hyperlink.setAddress(value.getHref());
            cell.setHyperlink(hyperlink);
            cell.setCellStyle(styles.link);
            return;
        }
        cell.setCellValue(cap(value.getText()));
        cell.setCellStyle(styles.body);
    }

    private static void writeText(Cell cell, String text, CellStyle style) {
        if (text == null || text.isBlank()) {
            cell.setCellValue("—");
            cell.setCellStyle(style);
            return;
        }
        cell.setCellValue(cap(text));
        cell.setCellStyle(style);
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

    private static void applyWidths(Sheet sheet, int[] widths) {
        for (int c = 0; c < widths.length; c++) {
            sheet.setColumnWidth(c, Math.min(70, Math.max(10, widths[c])) * 256);
        }
    }

    private static int displayLength(String text) {
        if (text == null) {
            return 0;
        }
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            length += text.charAt(i) > 0x7F ? 2 : 1;
        }
        return Math.min(length, 140);
    }

    private static String sheetName(String title, Set<String> used) {
        String base = title == null ? "数据" : title.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (base.isEmpty()) {
            base = "数据";
        }
        base = base.substring(0, Math.min(MAX_SHEET_NAME, base.length()));
        String name = base;
        int index = 2;
        while (!used.add(name)) {
            String suffix = "(" + index++ + ")";
            int keep = Math.max(1, MAX_SHEET_NAME - suffix.length());
            name = base.substring(0, Math.min(keep, base.length())) + suffix;
        }
        return name;
    }

    private static String defaultViewTitle(String type) {
        return switch (type) {
            case "table" -> "数据明细";
            case "chart" -> "数据图表";
            case "fields" -> "详情字段";
            case "list" -> "数据清单";
            case "file" -> "相关文件";
            default -> "分析结果";
        };
    }

    private static String cap(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_CELL_CHARS ? text.substring(0, MAX_CELL_CHARS) + "…" : text;
    }

    /** 单元格样式集中创建，避免逐单元格重复建样式。 */    private static final class Styles {

        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle number;
        private final CellStyle link;
        private final CellStyle muted;
        private final CellStyle code;

        private Styles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setFontName("微软雅黑");
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 15);
            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font sectionFont = workbook.createFont();
            sectionFont.setFontName("微软雅黑");
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 11);
            sectionFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            section = workbook.createCellStyle();
            section.setFont(sectionFont);

            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            header = border(workbook.createCellStyle());
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.LEFT);

            Font bodyFont = workbook.createFont();
            bodyFont.setFontName("微软雅黑");
            bodyFont.setFontHeightInPoints((short) 10);
            body = wrap(workbook.createCellStyle());
            body.setFont(bodyFont);

            number = wrap(workbook.createCellStyle());
            number.setFont(bodyFont);
            number.setAlignment(HorizontalAlignment.RIGHT);

            Font linkFont = workbook.createFont();
            linkFont.setFontName("微软雅黑");
            linkFont.setFontHeightInPoints((short) 10);
            linkFont.setUnderline(Font.U_SINGLE);
            linkFont.setColor(IndexedColors.BLUE.getIndex());
            link = wrap(workbook.createCellStyle());
            link.setFont(linkFont);

            Font mutedFont = workbook.createFont();
            mutedFont.setFontName("微软雅黑");
            mutedFont.setFontHeightInPoints((short) 10);
            mutedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            muted = wrap(workbook.createCellStyle());
            muted.setFont(mutedFont);

            Font codeFont = workbook.createFont();
            codeFont.setFontName("Consolas");
            codeFont.setFontHeightInPoints((short) 9);
            code = wrap(workbook.createCellStyle());
            code.setFont(codeFont);
        }

        private static CellStyle border(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        private static CellStyle wrap(CellStyle style) {
            border(style);
            style.setWrapText(true);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            return style;
        }
    }
}
