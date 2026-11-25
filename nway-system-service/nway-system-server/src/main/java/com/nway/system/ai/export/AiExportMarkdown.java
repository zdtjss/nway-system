package com.nway.system.ai.export;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 Markdown 解析：把 AI 正文拆成标题、段落、列表、代码块、引用与 GFM 表格。
 * <p>
 * 只覆盖 AI 回复常见的 Markdown 子集，供 Word / PPT / Excel / HTML 各自的渲染器复用。
 *
 * @author nway
 */
public final class AiExportMarkdown {

    private AiExportMarkdown() {
    }

    public enum BlockType {
        HEADING, PARAGRAPH, BULLET, ORDERED, CODE, QUOTE, TABLE, HR
    }

    /**
     * @param type   块类型
     * @param level  标题级别（1-6）或有序列表序号
     * @param marker 有序列表编号文本，如 "1."
     * @param text   文本内容（代码块保留换行）
     * @param table  表格行，首行为表头
     */
    public record MdBlock(BlockType type, int level, String marker, String text, List<List<String>> table) {
    }

    public enum InlineType {
        TEXT, BOLD, ITALIC, CODE, LINK, STRIKE
    }

    public record Inline(InlineType type, String text, String href) {
    }

    /** 解析为块级结构；空文本返回空列表。 */
    public static List<MdBlock> parse(String markdown) {
        List<MdBlock> blocks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return blocks;
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder paragraph = new StringBuilder();
        int index = 0;
        while (index < lines.length) {
            String line = lines[index];
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                flushParagraph(blocks, paragraph);
                index++;
                continue;
            }
            if (trimmed.startsWith("```")) {
                flushParagraph(blocks, paragraph);
                StringBuilder code = new StringBuilder();
                index++;
                while (index < lines.length && !lines[index].trim().startsWith("```")) {
                    if (code.length() > 0) {
                        code.append('\n');
                    }
                    code.append(lines[index]);
                    index++;
                }
                if (index < lines.length) {
                    index++;
                }
                blocks.add(new MdBlock(BlockType.CODE, 0, null, code.toString(), null));
                continue;
            }
            if (index + 1 < lines.length && line.contains("|") && isTableSeparator(lines[index + 1])) {
                flushParagraph(blocks, paragraph);
                List<List<String>> rows = new ArrayList<>();
                rows.add(splitRow(line));
                index += 2;
                while (index < lines.length && !lines[index].trim().isEmpty() && lines[index].contains("|")) {
                    rows.add(splitRow(lines[index]));
                    index++;
                }
                int columns = rows.stream().mapToInt(List::size).max().orElse(0);
                for (List<String> row : rows) {
                    while (row.size() < columns) {
                        row.add("");
                    }
                }
                blocks.add(new MdBlock(BlockType.TABLE, 0, null, null, rows));
                continue;
            }
            if (trimmed.matches("^#{1,6}\\s+.*")) {
                flushParagraph(blocks, paragraph);
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                blocks.add(new MdBlock(BlockType.HEADING, level, null, trimmed.substring(level).trim(), null));
                index++;
                continue;
            }
            if (trimmed.matches("^[-*+]\\s+.*")) {
                flushParagraph(blocks, paragraph);
                blocks.add(new MdBlock(BlockType.BULLET, 0, null, trimmed.substring(2).trim(), null));
                index++;
                continue;
            }
            if (trimmed.matches("^\\d{1,9}[.)]\\s+.*")) {
                flushParagraph(blocks, paragraph);
                int dot = 0;
                while (dot < trimmed.length() && Character.isDigit(trimmed.charAt(dot))) {
                    dot++;
                }
                blocks.add(new MdBlock(BlockType.ORDERED, 0, trimmed.substring(0, dot + 1),
                        trimmed.substring(dot + 1).trim(), null));
                index++;
                continue;
            }
            if (trimmed.startsWith(">")) {
                flushParagraph(blocks, paragraph);
                StringBuilder quote = new StringBuilder();
                while (index < lines.length && lines[index].trim().startsWith(">")) {
                    if (quote.length() > 0) {
                        quote.append('\n');
                    }
                    quote.append(lines[index].trim().replaceFirst("^>\\s?", ""));
                    index++;
                }
                blocks.add(new MdBlock(BlockType.QUOTE, 0, null, quote.toString(), null));
                continue;
            }
            if (trimmed.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                flushParagraph(blocks, paragraph);
                blocks.add(new MdBlock(BlockType.HR, 0, null, null, null));
                index++;
                continue;
            }
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 1) {
                // 缺少分隔行的伪表格：按普通段落保留，不丢内容
                appendParagraph(paragraph, trimmed);
                index++;
                continue;
            }
            appendParagraph(paragraph, trimmed);
            index++;
        }
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    /** 去除行内标记，用于 PPT / Excel 等只保留纯文本的场景。 */
    public static String plain(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Inline inline : inline(text)) {
            switch (inline.type()) {
                case LINK -> {
                    sb.append(inline.text());
                    if (inline.href() != null && !inline.href().isBlank() && !inline.href().equals(inline.text())) {
                        sb.append("（").append(inline.href()).append("）");
                    }
                }
                default -> sb.append(inline.text());
            }
        }
        return sb.toString();
    }

    /** 行内格式拆分：粗体、斜体、行内代码、链接与删除线。 */
    public static List<Inline> inline(String text) {
        List<Inline> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        StringBuilder buffer = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '[') {
                int close = text.indexOf(']', index + 1);
                if (close > 0 && close + 1 < text.length() && text.charAt(close + 1) == '(') {
                    int end = text.indexOf(')', close + 2);
                    if (end > close) {
                        flushInline(result, buffer);
                        String label = text.substring(index + 1, close);
                        String href = text.substring(close + 2, end).trim();
                        result.add(new Inline(InlineType.LINK, label.isEmpty() ? href : label, href));
                        index = end + 1;
                        continue;
                    }
                }
            }
            if (current == '`') {
                int end = text.indexOf('`', index + 1);
                if (end > index) {
                    flushInline(result, buffer);
                    result.add(new Inline(InlineType.CODE, text.substring(index + 1, end), null));
                    index = end + 1;
                    continue;
                }
            }
            if (current == '*' || current == '_' || current == '~') {
                boolean doubled = index + 1 < text.length() && text.charAt(index + 1) == current;
                if (current == '~' && !doubled) {
                    buffer.append(current);
                    index++;
                    continue;
                }
                String marker = doubled ? String.valueOf(new char[]{current, current}) : String.valueOf(current);
                int end = text.indexOf(marker, index + marker.length());
                if (end > index) {
                    flushInline(result, buffer);
                    InlineType type = doubled
                            ? (current == '~' ? InlineType.STRIKE : InlineType.BOLD)
                            : InlineType.ITALIC;
                    result.add(new Inline(type, text.substring(index + marker.length(), end), null));
                    index = end + marker.length();
                    continue;
                }
            }
            buffer.append(current);
            index++;
        }
        flushInline(result, buffer);
        return result;
    }

    private static void flushInline(List<Inline> result, StringBuilder buffer) {
        if (buffer.length() > 0) {
            result.add(new Inline(InlineType.TEXT, buffer.toString(), null));
            buffer.setLength(0);
        }
    }

    private static void flushParagraph(List<MdBlock> blocks, StringBuilder paragraph) {
        if (paragraph.length() == 0) {
            return;
        }
        blocks.add(new MdBlock(BlockType.PARAGRAPH, 0, null, paragraph.toString(), null));
        paragraph.setLength(0);
    }

    private static void appendParagraph(StringBuilder paragraph, String line) {
        if (paragraph.length() > 0) {
            paragraph.append('\n');
        }
        paragraph.append(line);
    }

    private static boolean isTableSeparator(String line) {
        String text = line.trim();
        if (!text.contains("-") || !text.contains("|")) {
            return false;
        }
        List<String> cells = splitRow(text);
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            if (!cell.trim().matches(":?-{2,}:?")) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitRow(String line) {
        String text = line.trim();
        if (text.startsWith("|")) {
            text = text.substring(1);
        }
        if (text.endsWith("|")) {
            text = text.substring(0, text.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : text.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }
}
