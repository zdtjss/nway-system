package com.nway.system.ai.web.model;

import com.alibaba.fastjson2.JSON;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * AI 分析结果导出请求。
 * <p>
 * 模型按导出意图组织内容：正文与 nway-view 结构一致。
 * 为兼容 nway-view 的两种表格写法（对象行 / 单元格数组），行数据先按 Object 接收，
 * 由 {@link View#tableRows()} 统一转换为单元格矩阵；字段与清单同理。
 *
 * @author nway
 */
@Data
public class AiExportRequest {

    /** docx / pptx / xlsx / html */
    private String format;

    /** 报告标题，通常取会话标题 */
    private String title;

    private String conversationId;

    private String messageId;

    private List<Block> blocks;

    @Data
    public static class Block {

        /** text：Markdown 正文；view：结构化视图 */
        private String type;

        private String text;

        private View view;
    }

    /** 与前端 AiView 对齐的结构化视图。 */
    @Data
    public static class View {

        private String type;

        private String title;

        private List<Column> columns;

        /** nway-view 对象行或单元格数组，统一通过 {@link #tableRows()} 转换 */
        private List<Object> rows;

        private Integer total;

        private List<Metric> summary;

        private List<Field> fields;

        /** 清单项，nway-view 中可能是字符串、数字或对象 */
        private List<Object> list;

        /** nway-view list 类型的标准字段名 */
        private List<Object> items;

        private List<Metric> metrics;

        private Chart chart;

        private List<FileItem> files;

        private String text;

        private String message;

        private Boolean success;

        private Boolean truncated;

        /** 表格列：优先使用声明列，未声明时从对象行推断。 */
        public List<Column> effectiveColumns() {
            if (columns != null && !columns.isEmpty()) {
                return columns;
            }
            if (rows != null) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> map && !map.isEmpty()) {
                        List<Column> derived = new ArrayList<>();
                        for (Object key : map.keySet()) {
                            Column column = new Column();
                            column.setKey(String.valueOf(key));
                            column.setLabel(String.valueOf(key));
                            derived.add(column);
                        }
                        return derived;
                    }
                }
            }
            return columns == null ? List.of() : columns;
        }

        /** 统一的行数据：对象行按列顺序取值，单元格数组逐项转换。 */
        public List<List<Cell>> tableRows() {
            List<List<Cell>> result = new ArrayList<>();
            if (rows == null) {
                return result;
            }
            List<Column> effective = effectiveColumns();
            for (Object row : rows) {
                List<Cell> cells = new ArrayList<>();
                if (row instanceof Map<?, ?> map) {
                    if (!effective.isEmpty()) {
                        for (Column column : effective) {
                            cells.add(toCell(map.get(column.getKey())));
                        }
                    } else {
                        for (Object value : map.values()) {
                            cells.add(toCell(value));
                        }
                    }
                } else if (row instanceof List<?> values) {
                    for (Object value : values) {
                        cells.add(toCell(value));
                    }
                } else {
                    cells.add(toCell(row));
                }
                result.add(cells);
            }
            return result;
        }

        public List<Cell> listCells() {
            List<Cell> result = new ArrayList<>();
            List<Object> source = list != null && !list.isEmpty() ? list : items;
            if (source == null) {
                return result;
            }
            for (Object item : source) {
                result.add(toCell(item));
            }
            return result;
        }
    }

    @Data
    public static class Column {

        private String key;

        private String label;

        private String type;
    }

    @Data
    public static class Cell {

        /** text / number / boolean / link / code / empty */
        private String kind;

        private String text;

        private Boolean bool;

        private String href;
    }

    @Data
    public static class Metric {

        private String label;

        private String value;

        private String unit;

        private String trend;
    }

    @Data
    public static class Field {

        private String key;

        private String label;

        /** nway-view 中可能是字符串、数字或 {text, kind} 对象 */
        private Object value;

        public Cell cellValue() {
            return toCell(value);
        }
    }

    @Data
    public static class Chart {

        /** bar / line / pie */
        private String kind;

        private List<String> categories;

        private List<Series> series;
    }

    @Data
    public static class Series {

        private String name;

        private List<Double> data;
    }

    @Data
    public static class FileItem {

        private String name;

        private String url;

        private String size;
    }

    /**
     * 任意值转单元格：兼容 nway-view 的原始值（字符串/数字/布尔/对象）与归一化单元格。
     */
    public static Cell toCell(Object raw) {
        if (raw == null) {
            return cell("—", "empty");
        }
        if (raw instanceof Cell cell) {
            return cell;
        }
        if (raw instanceof Map<?, ?> map) {
            if (map.containsKey("text") || map.containsKey("kind")) {
                Cell cell = new Cell();
                cell.setKind(map.get("kind") == null ? null : String.valueOf(map.get("kind")));
                cell.setText(map.get("text") == null ? null : String.valueOf(map.get("text")));
                if (map.get("bool") instanceof Boolean bool) {
                    cell.setBool(bool);
                }
                if (map.get("href") != null) {
                    cell.setHref(String.valueOf(map.get("href")));
                }
                return cell;
            }
            return cell(JSON.toJSONString(raw), "code");
        }
        if (raw instanceof Boolean bool) {
            Cell cell = cell(bool ? "是" : "否", "boolean");
            cell.setBool(bool);
            return cell;
        }
        if (raw instanceof Number number) {
            return cell(String.valueOf(number), "number");
        }
        if (raw instanceof String text) {
            if (text.matches("^https?://.+")) {
                Cell cell = cell(text, "link");
                cell.setHref(text);
                return cell;
            }
            return cell(text, "text");
        }
        if (raw instanceof Collection<?> || raw.getClass().isArray()) {
            return cell(JSON.toJSONString(raw), "code");
        }
        return cell(String.valueOf(raw), "text");
    }

    private static Cell cell(String text, String kind) {
        Cell cell = new Cell();
        cell.setText(text);
        cell.setKind(kind);
        return cell;
    }
}
