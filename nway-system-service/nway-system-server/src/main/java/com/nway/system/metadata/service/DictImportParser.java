package com.nway.system.metadata.service;

import com.nway.system.metadata.web.model.DictFieldDef;
import com.nway.system.common.exception.NwaySystemException;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 字典项导入文件解析器：支持 Excel（.xlsx/.xls）与 CSV。
 * <p>
 * 文件第一行为表头：
 * <ul>
 *   <li>传统 Key/Value 字典：Key、Value 列，可选 排序、状态 列</li>
 *   <li>多字段字典：列名使用字段编码或字段名称，可选 排序、状态 列</li>
 * </ul>
 * 解析结果按行返回，行级错误统一收集（不中断解析），由调用方决定是否落库。
 */
public final class DictImportParser {

    /** 传统字典项模式的匹配编码 */
    public static final String MATCH_KEY = "key";

    /** 单次导入最多返回的错误条数，避免超大文件产生过量提示 */
    private static final int MAX_ERRORS = 100;

    private static final Set<String> SORT_ALIASES = Set.of("排序", "sort");

    private static final Set<String> STATUS_ALIASES = Set.of("状态", "status");

    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "是", "启用", "yes", "y");

    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "否", "禁用", "no", "n");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE_PARSER = DateTimeFormatter.ofPattern("uuuu-M-d");

    private static final List<DateTimeFormatter> DATETIME_PARSERS = List.of(
            DateTimeFormatter.ofPattern("uuuu-M-d H:m:s"),
            DateTimeFormatter.ofPattern("uuuu-M-d H:m"));

    private static final DictFieldDef KEY_DEF = simpleDef("key", "Key");

    private static final DictFieldDef VALUE_DEF = simpleDef("value", "Value");

    private DictImportParser() {
    }

    /**
     * 一行导入数据
     *
     * @param rowNum    文件行号（含表头，从 1 开始）
     * @param itemValue 字段编码 → 值（空单元格不写入）
     * @param sort      排序，未提供为 null
     * @param status    状态 using/disabled，未提供为 null
     */
    public record ImportRow(int rowNum, Map<String, Object> itemValue, Integer sort, String status) {
    }

    /**
     * 解析结果：校验不通过时 rows 仍包含有效行，但 errors 非空，调用方不应落库
     */
    public record ParseResult(List<ImportRow> rows, List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * 解析导入文件
     *
     * @param file       上传文件
     * @param fieldDefs  字典字段定义，空表示传统 Key/Value 模式
     * @param matchField 多字段模式的匹配字段编码（传统模式忽略）
     */
    public static ParseResult parse(MultipartFile file, List<DictFieldDef> fieldDefs, String matchField)
            throws IOException {
        boolean simple = fieldDefs == null || fieldDefs.isEmpty();
        List<List<String>> table = readTable(file);
        if (table.isEmpty()) {
            throw new NwaySystemException("导入文件为空，请填写数据后重试");
        }

        List<String> headerErrors = new ArrayList<>();
        Map<Integer, DictFieldDef> fieldColumns = new LinkedHashMap<>();
        Map<String, Integer> mappedCodes = new HashMap<>();
        Integer sortColumn = null;
        Integer statusColumn = null;

        List<String> headers = table.get(0);
        for (int i = 0; i < headers.size(); i++) {
            String header = normalize(headers.get(i));
            if (header.isEmpty()) {
                continue;
            }
            DictFieldDef def = resolveField(header, fieldDefs, simple, headerErrors);
            if (def != null) {
                if (mappedCodes.putIfAbsent(def.getCode(), i) != null) {
                    addError(headerErrors, "表头【" + header + "】重复");
                } else {
                    fieldColumns.put(i, def);
                }
                continue;
            }
            if (isAlias(header, SORT_ALIASES)) {
                if (sortColumn != null) {
                    addError(headerErrors, "表头【" + header + "】重复");
                } else {
                    sortColumn = i;
                }
                continue;
            }
            if (isAlias(header, STATUS_ALIASES)) {
                if (statusColumn != null) {
                    addError(headerErrors, "表头【" + header + "】重复");
                } else {
                    statusColumn = i;
                }
                continue;
            }
            addError(headerErrors, "未知列【" + header + "】");
        }

        String matchCode = simple ? MATCH_KEY : matchField;
        String matchLabel = simple ? "Key" : "匹配字段【" + defName(fieldDefs, matchCode) + "】";
        if (simple) {
            if (!mappedCodes.containsKey(MATCH_KEY)) {
                addError(headerErrors, "缺少Key列");
            }
        } else {
            if (!mappedCodes.containsKey(matchCode)) {
                addError(headerErrors, "缺少" + matchLabel + "列");
            }
            for (DictFieldDef def : fieldDefs) {
                if (Boolean.TRUE.equals(def.getRequired()) && !mappedCodes.containsKey(def.getCode())) {
                    addError(headerErrors, "缺少必填字段列【" + def.getName() + "】");
                }
            }
        }
        if (!headerErrors.isEmpty()) {
            throw new NwaySystemException("表头校验失败：" + String.join("；", headerErrors));
        }

        List<ImportRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<DictFieldDef> defs = simple ? List.of(KEY_DEF, VALUE_DEF) : fieldDefs;
        for (int i = 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            if (isBlankRow(cells)) {
                continue;
            }
            int rowNum = i + 1;
            Map<String, Object> values = new LinkedHashMap<>();
            boolean valid = true;
            for (Map.Entry<Integer, DictFieldDef> entry : fieldColumns.entrySet()) {
                DictFieldDef def = entry.getValue();
                String text = cellAt(cells, entry.getKey());
                try {
                    Object value = convert(text, def);
                    if (value != null) {
                        values.put(def.getCode(), value);
                    }
                } catch (IllegalArgumentException e) {
                    addError(errors, "第" + rowNum + "行：" + e.getMessage());
                    valid = false;
                }
            }

            Integer sort = null;
            if (sortColumn != null) {
                String text = cellAt(cells, sortColumn);
                if (!text.isEmpty()) {
                    try {
                        int parsed = Integer.parseInt(text);
                        if (parsed < 0) {
                            throw new NumberFormatException();
                        }
                        sort = parsed;
                    } catch (NumberFormatException e) {
                        addError(errors, "第" + rowNum + "行：排序【" + text + "】必须为非负整数");
                        valid = false;
                    }
                }
            }

            String status = null;
            if (statusColumn != null) {
                String text = cellAt(cells, statusColumn);
                if (!text.isEmpty()) {
                    status = parseStatus(text);
                    if (status == null) {
                        addError(errors, "第" + rowNum + "行：状态【" + text + "】无法识别，请填写启用或禁用");
                        valid = false;
                    }
                }
            }

            Object matchValue = values.get(matchCode);
            if (matchValue == null || String.valueOf(matchValue).isBlank()) {
                addError(errors, "第" + rowNum + "行：" + matchLabel + "不能为空");
                valid = false;
            }
            for (DictFieldDef def : defs) {
                if (def.getCode().equals(matchCode) || !Boolean.TRUE.equals(def.getRequired())) {
                    continue;
                }
                if (!values.containsKey(def.getCode())) {
                    addError(errors, "第" + rowNum + "行：字段【" + def.getName() + "】不能为空");
                    valid = false;
                }
            }
            if (valid) {
                rows.add(new ImportRow(rowNum, values, sort, status));
            }
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new NwaySystemException("导入文件中没有可导入的数据行");
        }
        return new ParseResult(rows, errors);
    }

    private static DictFieldDef resolveField(String header, List<DictFieldDef> fieldDefs, boolean simple,
                                             List<String> headerErrors) {
        if (simple) {
            if (header.equalsIgnoreCase("key") || "键".equals(header)) {
                return KEY_DEF;
            }
            if (header.equalsIgnoreCase("value") || "值".equals(header)) {
                return VALUE_DEF;
            }
            return null;
        }
        List<DictFieldDef> matched = fieldDefs.stream()
                .filter(d -> header.equalsIgnoreCase(d.getCode()) || header.equals(d.getName()))
                .toList();
        if (matched.size() > 1) {
            addError(headerErrors, "表头【" + header + "】匹配到多个字段，请改用字段编码");
            return null;
        }
        return matched.isEmpty() ? null : matched.get(0);
    }

    private static Object convert(String raw, DictFieldDef def) {
        String text = normalize(raw);
        if (text.isEmpty()) {
            return null;
        }
        String type = def.getType() == null ? "string" : def.getType();
        return switch (type) {
            case "number" -> parseNumber(text, def);
            case "date" -> parseDate(text, def);
            case "datetime" -> parseDateTime(text, def);
            case "boolean" -> parseBoolean(text, def);
            default -> text;
        };
    }

    private static BigDecimal parseNumber(String text, DictFieldDef def) {
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "字段【" + def.getName() + "】的值【" + text + "】不是有效数字");
        }
    }

    private static String parseDate(String text, DictFieldDef def) {
        String value = datePart(text);
        try {
            return DATE_FORMAT.format(LocalDate.parse(value, DATE_PARSER));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "字段【" + def.getName() + "】的值【" + text + "】不是有效日期，格式应为 yyyy-MM-dd");
        }
    }

    private static String parseDateTime(String text, DictFieldDef def) {
        String value = text.replace('/', '-').replace('T', ' ').trim();
        for (DateTimeFormatter parser : DATETIME_PARSERS) {
            try {
                return DATETIME_FORMAT.format(LocalDateTime.parse(value, parser));
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        try {
            return DATETIME_FORMAT.format(LocalDate.parse(value, DATE_PARSER).atStartOfDay());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "字段【" + def.getName() + "】的值【" + text + "】不是有效日期时间，格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private static Boolean parseBoolean(String text, DictFieldDef def) {
        String value = text.toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(value)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(value)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException(
                "字段【" + def.getName() + "】的值【" + text + "】无法识别为布尔值，请填写是或否");
    }

    private static String parseStatus(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(value)) {
            return "using";
        }
        if (FALSE_VALUES.contains(value)) {
            return "disabled";
        }
        return null;
    }

    private static String datePart(String text) {
        String value = text.replace('/', '-').trim();
        int space = value.indexOf(' ');
        return space > 0 ? value.substring(0, space) : value;
    }

    private static List<List<String>> readTable(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".csv")) {
            return readCsv(file.getBytes());
        }
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return readExcel(file);
        }
        throw new NwaySystemException("仅支持导入 Excel（.xlsx/.xls）或 CSV 文件");
    }

    private static List<List<String>> readExcel(MultipartFile file) throws IOException {
        List<List<String>> table = new ArrayList<>();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                return table;
            }
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        cells.add(cellText(row.getCell(c)));
                    }
                }
                table.add(cells);
            }
        }
        stripLeadingBlankRows(table);
        return table;
    }

    /**
     * 读取单元格文本：数字保持原值（避免千分位/科学计数法），日期统一成 yyyy-MM-dd[ HH:mm:ss]
     */
    private static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> normalize(cell.getStringCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? dateTimeText(cell.getLocalDateTimeCellValue())
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static String dateTimeText(LocalDateTime dateTime) {
        return LocalTime.MIDNIGHT.equals(dateTime.toLocalTime())
                ? DATE_FORMAT.format(dateTime.toLocalDate())
                : DATETIME_FORMAT.format(dateTime);
    }

    private static List<List<String>> readCsv(byte[] bytes) {
        String text = decode(bytes);
        char delimiter = detectDelimiter(text);
        List<List<String>> table = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                inQuotes = true;
            } else if (ch == delimiter) {
                row.add(normalize(cell.toString()));
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(normalize(cell.toString()));
                table.add(row);
                row = new ArrayList<>();
                cell.setLength(0);
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(normalize(cell.toString()));
            table.add(row);
        }
        stripLeadingBlankRows(table);
        return table;
    }

    /**
     * 编码识别：优先 UTF-8（含 BOM），非法 UTF-8 时回退 GBK（Excel 导出的 CSV 常见编码）
     */
    private static String decode(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String firstLine = end >= 0 ? text.substring(0, end) : text;
        int comma = 0;
        int semicolon = 0;
        int tab = 0;
        boolean inQuotes = false;
        for (int i = 0; i < firstLine.length(); i++) {
            char ch = firstLine.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (ch == ',') {
                    comma++;
                } else if (ch == ';') {
                    semicolon++;
                } else if (ch == '\t') {
                    tab++;
                }
            }
        }
        if (tab > comma && tab > semicolon) {
            return '\t';
        }
        return semicolon > comma ? ';' : ',';
    }

    private static void stripLeadingBlankRows(List<List<String>> table) {
        while (!table.isEmpty() && isBlankRow(table.get(0))) {
            table.remove(0);
        }
    }

    private static boolean isBlankRow(List<String> cells) {
        if (cells == null) {
            return true;
        }
        for (String cell : cells) {
            if (StringUtils.isNotBlank(cell)) {
                return false;
            }
        }
        return true;
    }

    private static String cellAt(List<String> cells, int index) {
        return index < cells.size() ? normalize(cells.get(index)) : "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isAlias(String header, Set<String> aliases) {
        return aliases.contains(header) || aliases.contains(header.toLowerCase(Locale.ROOT));
    }

    private static String defName(List<DictFieldDef> defs, String code) {
        if (defs == null) {
            return code;
        }
        return defs.stream()
                .filter(d -> code.equals(d.getCode()))
                .map(DictFieldDef::getName)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(code);
    }

    private static DictFieldDef simpleDef(String code, String name) {
        DictFieldDef def = new DictFieldDef();
        def.setCode(code);
        def.setName(name);
        def.setType("string");
        return def;
    }

    private static void addError(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        } else if (errors.size() == MAX_ERRORS) {
            errors.add("错误数量过多，仅显示前 " + MAX_ERRORS + " 条，请修正后重新导入");
        }
    }
}
