package com.nway.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiInvokeConfig;
import com.nway.system.ai.domain.AiOutputField;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.web.model.AiDataExportRequest;
import com.nway.system.ai.web.model.AiExportRequest;
import com.nway.system.ai.web.model.AiExportVo;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.file.domain.FileTicket;
import com.nway.system.file.service.FileService;
import com.nway.system.file.web.model.FileTicketVo;
import com.nway.system.file.web.model.FileVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 大数据确定性导出：模型只提供查询计划，服务端分页取全量、对账完整性并直接写文件。
 * <p>
 * 模型全程看不到数据行；只拿到每个查询的行数、页数与完整性。任一分页失败、单页被截断、
 * 无法保证全量或超过导线上限时，不生成文件并返回 handoff。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiDataExportService {

    private static final Set<String> FORMATS = Set.of("xlsx", "csv", "html", "docx");
    private static final String BIZ_TYPE = "ai_export";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_FILE_TITLE_LENGTH = 60;
    private static final String DEFAULT_TITLE = "AI 数据导出";
    private static final int MAX_CELL_CHARS = 32_000;
    private static final List<String> PAGE_KEYS = List.of(
            "pageno", "pagenum", "page", "current", "pageindex", "page_index", "page_no", "page_num");
    private static final List<String> SIZE_KEYS = List.of(
            "pagesize", "size", "limit", "rows", "page_size", "perpage", "per_page");

    private final AiInterfaceCallService callService;
    private final AiDataSize dataSize;
    private final AiLimits limits;
    private final FileService fileService;
    private final AiExportService exportService;
    private final AiAuditService auditService;

    public AiDataExportService(AiInterfaceCallService callService, AiDataSize dataSize, AiLimits limits,
                               FileService fileService, AiExportService exportService,
                               AiAuditService auditService) {
        this.callService = callService;
        this.dataSize = dataSize;
        this.limits = limits;
        this.fileService = fileService;
        this.exportService = exportService;
        this.auditService = auditService;
    }

    public record QueryStat(String key, String interfaceCode, long rows, int pages, boolean complete,
                            String reason) {
    }

    public record ExportOutcome(boolean success, String error, String message, AiExportVo file,
                                long rows, List<QueryStat> queries, Map<String, Object> detail) {
    }

    private record PaginationSpec(boolean enabled, String pageParam, String sizeParam,
                                  int startPage, int pageSize, int maxPages) {
        static PaginationSpec none() {
            return new PaginationSpec(false, null, null, 1, 0, 0);
        }
    }

    private record PreparedQuery(AiDataExportRequest.Query query, AiInterfaceConfig config,
                                 Map<String, Object> args, PaginationSpec pagination, String displayName) {
    }

    private record FetchOutcome(QueryStat stat, String error, String message) {
        boolean complete() {
            return error == null;
        }
    }

    public ExportOutcome export(AiUserContext user, AiDataExportRequest request) {
        if (user == null) {
            return failure(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文", List.of(), null);
        }
        if (request == null || request.getQueries() == null || request.getQueries().isEmpty()) {
            return failure(AiErrors.INVALID_ARGUMENT, "queries 不能为空", List.of(), null);
        }
        String format = normalizeFormat(request.getFormat());
        if (format == null) {
            return failure(AiErrors.INVALID_ARGUMENT, "不支持的导出格式，请选择 xlsx、csv、html 或 docx",
                    List.of(), null);
        }
        if (request.getQueries().size() > limits.getQueryBatchMaxCalls()) {
            return failure(AiErrors.INVALID_ARGUMENT,
                    "一次最多导出 " + limits.getQueryBatchMaxCalls() + " 个查询", List.of(), null);
        }

        List<PreparedQuery> prepared = new ArrayList<>(request.getQueries().size());
        try {
            for (AiDataExportRequest.Query query : request.getQueries()) {
                PreparedQuery plan = prepare(user, query, format);
                if (plan == null) {
                    return failure(AiErrors.INVALID_ARGUMENT, "查询计划不完整：" + describe(query),
                            List.of(), null);
                }
                prepared.add(plan);
            }
        } catch (AiInterfaceCallService.CallException e) {
            return failure(e.getErrorCode(), e.getMessage(), List.of(), null);
        }

        return "docx".equals(format) ? exportDocx(user, request, prepared) : exportStreaming(user, request, prepared, format);
    }

    private PreparedQuery prepare(AiUserContext user, AiDataExportRequest.Query query, String format) {
        if (query == null || StringUtils.isBlank(query.getInterfaceCode())) {
            return null;
        }
        if (StringUtils.isNotBlank(query.getDependsOn())
                || (query.getForEach() != null && !query.getForEach().isEmpty())) {
            log.warn("导出计划包含暂不支持的依赖查询 interfaceCode={}", query.getInterfaceCode());
            throw new AiInterfaceCallService.CallException(AiErrors.INVALID_ARGUMENT,
                    "当前版本不支持 dependsOn/forEach 依赖导出，请拆分为多个导出请求");
        }
        AiInterfaceConfig config = callService.requireConfig(user, query.getInterfaceCode());
        callService.assertReadable(user, config);
        Map<String, Object> args = callService.validateArgs(user, config, query.getArgs());
        PaginationSpec pagination = resolvePagination(config, query.getPagination());
        String displayName = StringUtils.defaultIfBlank(query.getSheetName(),
                StringUtils.defaultIfBlank(query.getKey(), config.getName()));
        return new PreparedQuery(query, config, args, pagination, displayName);
    }

    // ==================== 查询计划 ====================

    /**
     * 分页参数识别：显式配置优先；未配置时从 inputSchema 自动识别 page/size 字段。
     * 识别不到分页字段时按单次取回处理，由完整性对账决定是否允许导出。
     */
    private PaginationSpec resolvePagination(AiInterfaceConfig config, AiDataExportRequest.Pagination explicit) {
        AiInvokeConfig.Pagination meta = config.getInvokeConfig() == null
                ? null : config.getInvokeConfig().getPagination();
        int requestedSize = explicit != null && explicit.getPageSize() != null
                ? explicit.getPageSize()
                : meta != null && meta.getPageSize() != null ? meta.getPageSize() : limits.getExportPageSize();
        int startPage = explicit != null && explicit.getStartPage() != null
                ? explicit.getStartPage()
                : meta != null && meta.getStartPage() != null ? meta.getStartPage() : 1;
        int maxPages = explicit != null && explicit.getMaxPages() != null
                ? Math.min(explicit.getMaxPages(), limits.getExportMaxPages())
                : meta != null && meta.getMaxPages() != null
                ? Math.min(meta.getMaxPages(), limits.getExportMaxPages())
                : limits.getExportMaxPages();
        String mode = explicit == null ? null : StringUtils.upperCase(explicit.getMode());
        if (StringUtils.isBlank(mode) && meta != null) {
            mode = StringUtils.upperCase(meta.getMode());
        }
        if ("NONE".equals(mode)) {
            return PaginationSpec.none();
        }

        String pageParam = explicit == null ? null : StringUtils.trimToNull(explicit.getPageParam());
        String sizeParam = explicit == null ? null : StringUtils.trimToNull(explicit.getSizeParam());
        if (pageParam == null && meta != null) {
            pageParam = StringUtils.trimToNull(meta.getPageParam());
        }
        if (sizeParam == null && meta != null) {
            sizeParam = StringUtils.trimToNull(meta.getSizeParam());
        }
        if (pageParam == null) {
            pageParam = detectField(config, PAGE_KEYS);
        }
        if (sizeParam == null) {
            sizeParam = detectField(config, SIZE_KEYS);
        }
        if (pageParam == null || sizeParam == null
                || !schemaAllows(config, pageParam) || !schemaAllows(config, sizeParam)) {
            return PaginationSpec.none();
        }
        int pageSize = resolvePageSize(config, sizeParam, requestedSize);
        return new PaginationSpec(true, pageParam, sizeParam, Math.max(0, startPage), pageSize,
                Math.max(1, maxPages));
    }

    private String detectField(AiInterfaceConfig config, List<String> candidates) {
        if (config.getInputSchema() == null) {
            return null;
        }
        for (String candidate : candidates) {
            for (var field : config.getInputSchema()) {
                if (field.isLlmVisible() && field.getKey() != null
                        && candidate.equalsIgnoreCase(field.getKey())) {
                    return field.getKey();
                }
            }
        }
        return null;
    }

    /** 未声明入参的接口允许任意参数；声明了入参时，分页参数必须已定义，否则校验会拒绝。 */
    private boolean schemaAllows(AiInterfaceConfig config, String key) {
        if (config.getInputSchema() == null || config.getInputSchema().isEmpty()) {
            return true;
        }
        return config.getInputSchema().stream()
                .anyMatch(field -> field.isLlmVisible() && key.equalsIgnoreCase(field.getKey()));
    }

    private int resolvePageSize(AiInterfaceConfig config, String sizeParam, int requested) {
        int pageSize = Math.max(1, Math.min(requested, 1000));
        if (config.getInputSchema() == null) {
            return pageSize;
        }
        for (var field : config.getInputSchema()) {
            if (!field.isLlmVisible() || field.getKey() == null
                    || !sizeParam.equalsIgnoreCase(field.getKey())) {
                continue;
            }
            Map<String, Object> validation = field.getValidation();
            if (validation == null) {
                return pageSize;
            }
            if (validation.get("max") instanceof Number max) {
                pageSize = Math.min(pageSize, max.intValue());
            }
            if (validation.get("min") instanceof Number min) {
                pageSize = Math.max(pageSize, min.intValue());
            }
            return Math.max(1, pageSize);
        }
        return pageSize;
    }

    // ==================== 流式导出 ====================

    private ExportOutcome exportStreaming(AiUserContext user, AiDataExportRequest request,
                                          List<PreparedQuery> prepared, String format) {
        Path temp = null;
        StreamWriter writer = createWriter(format);
        try {
            List<QueryStat> stats = new ArrayList<>(prepared.size());
            long totalRows = 0;
            for (PreparedQuery plan : prepared) {
                FetchOutcome outcome = fetchAndWrite(user, plan, writer);
                stats.add(outcome.stat());
                if (!outcome.complete()) {
                    return failure(outcome.error(), outcome.message(), stats, detail(stats));
                }
                totalRows += outcome.stat().rows();
            }
            temp = writer.finish();
            long fileBytes = Files.size(temp);
            if (fileBytes > limits.getExportMaxBytes()) {
                return failure(AiErrors.EXPORT_LIMIT_EXCEEDED,
                        "导出文件超过上限（" + (limits.getExportMaxBytes() / 1024 / 1024)
                                + "MB），请缩小查询范围或拆分导出", stats, detail(stats));
            }
            String fileName = buildFileName(normalizeTitle(request.getTitle()), format);
            FileVo file;
            try (InputStream in = Files.newInputStream(temp)) {
                file = fileService.saveGenerated(in, fileBytes, fileName, BIZ_TYPE,
                        StringUtils.left(user.getConversationId(), 64), "AI 数据导出");
            }
            FileTicketVo ticket = fileService.createTicket(file.getId(), FileTicket.PURPOSE_DOWNLOAD);
            AiExportVo vo = new AiExportVo();
            vo.setFileId(file.getId());
            vo.setFileName(file.getFileName());
            vo.setSize(file.getFileSize());
            vo.setUrl(ticket.getUrl());
            auditService.recordDataExport(user, auditSummary(request, stats, totalRows));
            log.info("AI 数据导出完成 format={} fileId={} rows={} queries={} size={}", format,
                    file.getId(), totalRows, stats.size(), file.getFileSize());
            return new ExportOutcome(true, null, "文件已生成，点击对话中的文件名即可下载", vo,
                    totalRows, stats, detail(stats));
        } catch (AiInterfaceCallService.CallException e) {
            return failure(e.getErrorCode(), e.getMessage(), List.of(), null);
        } catch (NwaySystemException e) {
            log.warn("AI 数据导出失败 format={} error={}", format, e.getMessage());
            return failure(AiErrors.EXPORT_LIMIT_EXCEEDED, e.getMessage(), List.of(), null);
        } catch (Exception e) {
            log.error("AI 数据导出异常 format={}", format, e);
            return failure(AiErrors.TARGET_ERROR, "导出失败，请稍后重试", List.of(), null);
        } finally {
            try {
                writer.close();
            } catch (Exception ignore) {
                // 临时资源清理失败不影响结果
            }
            deleteQuietly(temp);
        }
    }

    private FetchOutcome fetchAndWrite(AiUserContext user, PreparedQuery plan, StreamWriter writer) {
        long start = System.currentTimeMillis();
        writer.startQuery(plan.displayName());
        long written = 0;
        int pages = 0;
        Long total = null;
        List<Map<String, Object>> columns = null;
        String interfaceCode = plan.config().getInterfaceCode();
        String key = StringUtils.defaultIfBlank(plan.query().getKey(), interfaceCode);
        boolean complete;

        if (!plan.pagination().enabled()) {
            AiInterfaceCallService.CallResult result = callService.call(user, interfaceCode,
                    plan.args(), false, "export_query_data", "-", "-");
            pages = 1;
            if (!result.success()) {
                return incomplete(plan, key, written, pages, result.errorCode(), result.truncated()
                        ? "接口单次返回数据过大，无法保证完整；请缩小查询范围或使用支持分页的接口"
                        : result.message());
            }
            Object data = result.invoke().getData();
            total = dataSize.total(data);
            List<Object> rows = dataSize.extractRows(data, plan.config().getOutputSchema());
            if (rows.size() > limits.getExportMaxRows()) {
                return incomplete(plan, key, written, pages, AiErrors.EXPORT_LIMIT_EXCEEDED,
                        "数据量超过单次导出上限（" + limits.getExportMaxRows() + " 行）");
            }
            columns = dataSize.columns(rows.isEmpty() ? null : rows.get(0), plan.config().getOutputSchema());
            if (!rows.isEmpty()) {
                writer.writeBatch(rows, columns);
                written = rows.size();
            }
            complete = total == null || total == written;
            if (!complete) {
                return incomplete(plan, key, written, pages, AiErrors.DATA_INCOMPLETE,
                        "该接口不支持分页或单次返回不完整，无法保证全量导出（total=" + total
                                + "，已取=" + written + "）；请通过对应业务菜单导出，或联系系统管理员");
            }
            writer.endQuery(written);
            log.info("导出查询完成 interfaceCode={} mode=NONE rows={} total={} pages={} elapsed={}ms",
                    interfaceCode, written, total, pages, System.currentTimeMillis() - start);
            return new FetchOutcome(new QueryStat(key, interfaceCode, written, pages, true, null), null, null);
        }

        int page = plan.pagination().startPage();
        while (true) {
            if (pages >= plan.pagination().maxPages()) {
                return incomplete(plan, key, written, pages, AiErrors.EXPORT_LIMIT_EXCEEDED,
                        "导出页数超过上限（" + plan.pagination().maxPages() + " 页），无法保证全量");
            }
            Map<String, Object> args = new LinkedHashMap<>(plan.args());
            args.put(plan.pagination().pageParam(), page);
            args.put(plan.pagination().sizeParam(), plan.pagination().pageSize());
            AiInterfaceCallService.CallResult result = callService.call(user, interfaceCode,
                    args, false, "export_query_data", "-", "-");
            pages++;
            if (!result.success()) {
                return incomplete(plan, key, written, pages, result.errorCode(), result.truncated()
                        ? "第 " + page + " 页返回数据过大被截断，无法保证全量导出"
                        : "第 " + page + " 页查询失败：" + result.message());
            }
            Object data = result.invoke().getData();
            Long pageTotal = dataSize.total(data);
            if (pageTotal != null && total == null) {
                total = pageTotal;
            }
            List<Object> pageRows = dataSize.extractRows(data, plan.config().getOutputSchema());
            if (!pageRows.isEmpty()) {
                if (columns == null) {
                    columns = dataSize.columns(pageRows.get(0), plan.config().getOutputSchema());
                }
                if (written + pageRows.size() > limits.getExportMaxRows()) {
                    return incomplete(plan, key, written, pages, AiErrors.EXPORT_LIMIT_EXCEEDED,
                            "导出行数超过上限（" + limits.getExportMaxRows() + " 行）");
                }
                writer.writeBatch(pageRows, columns);
                written += pageRows.size();
            }
            if (pageRows.isEmpty()) {
                complete = total == null || total == written;
                if (!complete) {
                    return incomplete(plan, key, written, pages, AiErrors.DATA_INCOMPLETE,
                            "分页提前结束但记录数不一致（total=" + total + "，已取=" + written + "）");
                }
                break;
            }
            if (total != null && written >= total) {
                if (written > total) {
                    return incomplete(plan, key, written, pages, AiErrors.DATA_INCOMPLETE,
                            "分页返回重复数据，记录数与 total 不一致（total=" + total
                                    + "，已取=" + written + "）");
                }
                break;
            }
            if (total == null && pageRows.size() < plan.pagination().pageSize()) {
                break;
            }
            page++;
        }
        writer.endQuery(written);
        log.info("导出查询完成 interfaceCode={} mode=PAGE rows={} total={} pages={} elapsed={}ms",
                interfaceCode, written, total, pages, System.currentTimeMillis() - start);
        return new FetchOutcome(new QueryStat(key, interfaceCode, written, pages, true, null), null, null);
    }

    private FetchOutcome incomplete(PreparedQuery plan, String key, long written, int pages,
                                    String error, String message) {
        log.warn("导出查询不完整 interfaceCode={} rows={} pages={} error={} message={}",
                plan.config().getInterfaceCode(), written, pages, error, message);
        return new FetchOutcome(new QueryStat(key, plan.config().getInterfaceCode(), written, pages,
                false, error), error, message);
    }

    // ==================== docx（仅小数据） ====================

    private ExportOutcome exportDocx(AiUserContext user, AiDataExportRequest request,
                                     List<PreparedQuery> prepared) {
        List<QueryStat> stats = new ArrayList<>(prepared.size());
        List<AiExportRequest.Block> blocks = new ArrayList<>();
        long totalRows = 0;
        try {
            AiExportRequest exportRequest = new AiExportRequest();
            exportRequest.setFormat("docx");
            exportRequest.setTitle(normalizeTitle(request.getTitle()));
            exportRequest.setConversationId(user.getConversationId());
            for (PreparedQuery plan : prepared) {
                List<Object> rows = new ArrayList<>();
                int pages = 0;
                Long total;
                if (plan.pagination().enabled()) {
                    int page = plan.pagination().startPage();
                    total = null;
                    while (true) {
                        if (pages >= plan.pagination().maxPages()) {
                            return failure(AiErrors.EXPORT_LIMIT_EXCEEDED, "导出页数超过上限", stats,
                                    detail(stats));
                        }
                        Map<String, Object> args = new LinkedHashMap<>(plan.args());
                        args.put(plan.pagination().pageParam(), page);
                        args.put(plan.pagination().sizeParam(), plan.pagination().pageSize());
                        AiInterfaceCallService.CallResult result = callService.call(user,
                                plan.config().getInterfaceCode(), args, false, "export_query_data", "-", "-");
                        pages++;
                        if (!result.success()) {
                            return failure(result.errorCode(), result.message(), stats, detail(stats));
                        }
                        Object data = result.invoke().getData();
                        Long pageTotal = dataSize.total(data);
                        if (pageTotal != null && total == null) {
                            total = pageTotal;
                        }
                        List<Object> pageRows = dataSize.extractRows(data, plan.config().getOutputSchema());
                        rows.addAll(pageRows);
                        if (rows.size() > limits.getDocxMaxRows()) {
                            return failure(AiErrors.EXPORT_LIMIT_EXCEEDED,
                                    "docx 仅支持小数据报告（不超过 " + limits.getDocxMaxRows()
                                            + " 行），请改用 xlsx 导出", stats, detail(stats));
                        }
                        if (pageRows.isEmpty() || (total != null && rows.size() >= total)
                                || (total == null && pageRows.size() < plan.pagination().pageSize())) {
                            break;
                        }
                        page++;
                    }
                    if (total != null && rows.size() != total) {
                        return failure(AiErrors.DATA_INCOMPLETE,
                                "分页记录数与 total 不一致，无法保证全量导出（total=" + total
                                        + "，已取=" + rows.size() + "）", stats, detail(stats));
                    }
                } else {
                    AiInterfaceCallService.CallResult result = callService.call(user,
                            plan.config().getInterfaceCode(), plan.args(), false, "export_query_data", "-", "-");
                    pages = 1;
                    if (!result.success()) {
                        return failure(result.errorCode(), result.message(), stats, detail(stats));
                    }
                    Object data = result.invoke().getData();
                    rows.addAll(dataSize.extractRows(data, plan.config().getOutputSchema()));
                    total = dataSize.total(data);
                    if (total != null && total != rows.size()) {
                        return failure(AiErrors.DATA_INCOMPLETE,
                                "该接口不支持分页或单次返回不完整，无法保证全量导出", stats, detail(stats));
                    }
                    if (rows.size() > limits.getDocxMaxRows()) {
                        return failure(AiErrors.EXPORT_LIMIT_EXCEEDED,
                                "docx 仅支持小数据报告（不超过 " + limits.getDocxMaxRows()
                                        + " 行），请改用 xlsx 导出", stats, detail(stats));
                    }
                }
                List<Map<String, Object>> columns = dataSize.columns(
                        rows.isEmpty() ? null : rows.get(0), plan.config().getOutputSchema());
                blocks.add(tableBlock(plan.displayName(), columns, rows));
                stats.add(new QueryStat(StringUtils.defaultIfBlank(plan.query().getKey(),
                        plan.config().getInterfaceCode()), plan.config().getInterfaceCode(),
                        rows.size(), pages, true, null));
                totalRows += rows.size();
            }
            exportRequest.setBlocks(blocks);
            AiExportVo vo = exportService.export(exportRequest);
            auditService.recordDataExport(user, auditSummary(request, stats, totalRows));
            return new ExportOutcome(true, null, "文件已生成，点击对话中的文件名即可下载", vo,
                    totalRows, stats, detail(stats));
        } catch (AiInterfaceCallService.CallException e) {
            return failure(e.getErrorCode(), e.getMessage(), stats, detail(stats));
        } catch (NwaySystemException e) {
            return failure(AiErrors.EXPORT_LIMIT_EXCEEDED, e.getMessage(), stats, detail(stats));
        } catch (Exception e) {
            log.error("AI 数据 docx 导出异常", e);
            return failure(AiErrors.TARGET_ERROR, "导出失败，请稍后重试", stats, detail(stats));
        }
    }

    private AiExportRequest.Block tableBlock(String title, List<Map<String, Object>> columns,
                                             List<Object> rows) {
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
        view.setRows(rows);
        view.setTotal(rows.size());
        AiExportRequest.Block block = new AiExportRequest.Block();
        block.setType("view");
        block.setView(view);
        return block;
    }

    // ==================== 文件生成 ====================

    private StreamWriter createWriter(String format) {
        return switch (format) {
            case "csv" -> new CsvWriter();
            case "html" -> new HtmlWriter();
            default -> new XlsxWriter();
        };
    }

    private interface StreamWriter extends AutoCloseable {

        void startQuery(String name);

        void writeBatch(List<Object> rows, List<Map<String, Object>> columns);

        void endQuery(long rows);

        Path finish() throws IOException;

        @Override
        default void close() throws IOException {
        }
    }

    /** Excel：SXSSF 滑动窗口流式写，每个查询一个 Sheet。 */
    private static final class XlsxWriter implements StreamWriter {

        private final SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        private final Set<String> sheetNames = new HashSet<>();
        private final CellStyle headerStyle;
        private Sheet sheet;
        private int rowIndex;
        private boolean headerWritten;
        private boolean hasRows;
        private boolean finished;

        private XlsxWriter() {
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle = workbook.createCellStyle();
            headerStyle.setFont(font);
            workbook.createSheet("导出说明").createRow(0).createCell(0).setCellValue("AI 数据导出");
            rowIndex = 1;
        }

        @Override
        public void startQuery(String name) {
            sheet = workbook.createSheet(uniqueSheetName(name));
            rowIndex = 0;
            headerWritten = false;
            hasRows = false;
        }

        @Override
        public void writeBatch(List<Object> rows, List<Map<String, Object>> columns) {
            if (sheet == null) {
                return;
            }
            if (!headerWritten) {
                writeHeader(columns);
            }
            for (Object item : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int c = 0; c < columns.size(); c++) {
                    Object value = item instanceof Map<?, ?> map
                            ? map.get(columns.get(c).get("key"))
                            : (c == 0 ? item : null);
                    writeCell(row.createCell(c), value);
                }
            }
            hasRows = true;
        }

        @Override
        public void endQuery(long rows) {
            if (sheet != null && !hasRows) {
                sheet.createRow(rowIndex).createCell(0).setCellValue("（无数据）");
            }
        }

        private void writeHeader(List<Map<String, Object>> columns) {
            Row row = sheet.createRow(rowIndex++);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue(String.valueOf(columns.get(c).get("label")));
                cell.setCellStyle(headerStyle);
            }
            headerWritten = true;
        }

        private void writeCell(Cell cell, Object value) {
            if (value == null) {
                return;
            }
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof Boolean bool) {
                cell.setCellValue(bool);
            } else {
                String text = value instanceof String s ? s : JSON.toJSONString(value);
                cell.setCellValue(StringUtils.abbreviate(text, MAX_CELL_CHARS));
            }
        }

        private String uniqueSheetName(String raw) {
            String name = StringUtils.defaultIfBlank(raw, "数据")
                    .replaceAll("[\\\\/:*?\\[\\]\\r\\n\\t]", "_");
            if (name.length() > 31) {
                name = name.substring(0, 31);
            }
            String candidate = name;
            int index = 2;
            while (!sheetNames.add(candidate)) {
                String suffix = "(" + index++ + ")";
                candidate = name.length() + suffix.length() > 31
                        ? name.substring(0, 31 - suffix.length()) + suffix : name + suffix;
            }
            return candidate;
        }

        @Override
        public Path finish() throws IOException {
            Path temp = Files.createTempFile("ai-data-export-", ".xlsx");
            try (FileOutputStream out = new FileOutputStream(temp.toFile())) {
                workbook.write(out);
            }
            workbook.dispose();
            finished = true;
            return temp;
        }

        @Override
        public void close() throws IOException {
            if (!finished) {
                workbook.dispose();
            }
            workbook.close();
        }
    }

    /** CSV：UTF-8 + BOM，便于 Excel 直接打开中文。 */
    private static final class CsvWriter implements StreamWriter {

        private final Path temp;
        private final BufferedWriter writer;
        private boolean firstQuery = true;
        private boolean headerWritten;
        private boolean finished;

        private CsvWriter() {
            try {
                temp = Files.createTempFile("ai-data-export-", ".csv");
                writer = new BufferedWriter(new OutputStreamWriter(
                        Files.newOutputStream(temp), StandardCharsets.UTF_8));
                writer.write('\uFEFF');
            } catch (IOException e) {
                throw new NwaySystemException("创建导出临时文件失败", e);
            }
        }

        @Override
        public void startQuery(String name) {
            try {
                if (!firstQuery) {
                    writer.newLine();
                }
                writer.write("# " + name);
                writer.newLine();
                firstQuery = false;
                headerWritten = false;
            } catch (IOException e) {
                throw new NwaySystemException("写入导出文件失败", e);
            }
        }

        @Override
        public void writeBatch(List<Object> rows, List<Map<String, Object>> columns) {
            try {
                if (!headerWritten) {
                    writeRow(columns.stream().map(c -> c.get("label")).toList());
                    headerWritten = true;
                }
                for (Object item : rows) {
                    List<Object> cells = new ArrayList<>(columns.size());
                    for (int c = 0; c < columns.size(); c++) {
                        cells.add(item instanceof Map<?, ?> map
                                ? map.get(columns.get(c).get("key"))
                                : (c == 0 ? item : null));
                    }
                    writeRow(cells);
                }
            } catch (IOException e) {
                throw new NwaySystemException("写入导出文件失败", e);
            }
        }

        private void writeRow(List<Object> cells) throws IOException {
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                writer.write(escape(cells.get(i)));
            }
            writer.newLine();
        }

        private String escape(Object value) {
            if (value == null) {
                return "";
            }
            String text = value instanceof String s ? s : JSON.toJSONString(value);
            if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
                return '"' + text.replace("\"", "\"\"") + '"';
            }
            return text;
        }

        @Override
        public void endQuery(long rows) {
            if (!headerWritten && rows == 0) {
                try {
                    writer.write("（无数据）");
                    writer.newLine();
                } catch (IOException e) {
                    throw new NwaySystemException("写入导出文件失败", e);
                }
            }
        }

        @Override
        public Path finish() throws IOException {
            writer.flush();
            writer.close();
            finished = true;
            return temp;
        }

        @Override
        public void close() throws IOException {
            try {
                writer.close();
            } finally {
                if (!finished) {
                    Files.deleteIfExists(temp);
                }
            }
        }
    }

    /** HTML：流式写表格，浏览器可直接查看。 */
    private static final class HtmlWriter implements StreamWriter {

        private final Path temp;
        private final BufferedWriter writer;
        private boolean firstQuery = true;
        private boolean headerWritten;
        private boolean finished;

        private HtmlWriter() {
            try {
                temp = Files.createTempFile("ai-data-export-", ".html");
                writer = new BufferedWriter(new OutputStreamWriter(
                        Files.newOutputStream(temp), StandardCharsets.UTF_8));
                writer.write("<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                        + "<style>body{font-family:sans-serif;padding:16px}"
                        + "table{border-collapse:collapse;margin-bottom:24px}"
                        + "th,td{border:1px solid #ddd;padding:6px 10px;font-size:13px}"
                        + "th{background:#f5f7fa;text-align:left}</style></head><body>");
            } catch (IOException e) {
                throw new NwaySystemException("创建导出临时文件失败", e);
            }
        }

        @Override
        public void startQuery(String name) {
            try {
                if (!firstQuery) {
                    writer.write("</tbody></table>");
                }
                firstQuery = false;
                headerWritten = false;
                writer.write("<h2>" + escape(name) + "</h2><table><tbody>");
            } catch (IOException e) {
                throw new NwaySystemException("写入导出文件失败", e);
            }
        }

        @Override
        public void writeBatch(List<Object> rows, List<Map<String, Object>> columns) {
            try {
                if (!headerWritten) {
                    writer.write("<tr>");
                    for (Map<String, Object> column : columns) {
                        writer.write("<th>" + escape(String.valueOf(column.get("label"))) + "</th>");
                    }
                    writer.write("</tr>");
                    headerWritten = true;
                }
                for (Object item : rows) {
                    writer.write("<tr>");
                    for (int c = 0; c < columns.size(); c++) {
                        Object value = item instanceof Map<?, ?> map
                                ? map.get(columns.get(c).get("key"))
                                : (c == 0 ? item : null);
                        writer.write("<td>" + escape(value == null ? ""
                                : value instanceof String s ? s : JSON.toJSONString(value)) + "</td>");
                    }
                    writer.write("</tr>");
                }
            } catch (IOException e) {
                throw new NwaySystemException("写入导出文件失败", e);
            }
        }

        @Override
        public void endQuery(long rows) {
            if (!headerWritten) {
                try {
                    writer.write("<tr><td>（无数据）</td></tr>");
                } catch (IOException e) {
                    throw new NwaySystemException("写入导出文件失败", e);
                }
            }
        }

        private String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }

        @Override
        public Path finish() throws IOException {
            writer.write("</tbody></table></body></html>");
            writer.flush();
            writer.close();
            finished = true;
            return temp;
        }

        @Override
        public void close() throws IOException {
            try {
                writer.close();
            } finally {
                if (!finished) {
                    Files.deleteIfExists(temp);
                }
            }
        }
    }

    // ==================== 辅助 ====================

    private String normalizeFormat(String raw) {
        String format = StringUtils.lowerCase(StringUtils.trimToEmpty(raw), Locale.ROOT);
        if (format.startsWith(".")) {
            format = format.substring(1);
        }
        if (format.isEmpty()) {
            return "xlsx";
        }
        return FORMATS.contains(format) ? format : null;
    }

    private String normalizeTitle(String raw) {
        String title = StringUtils.trimToEmpty(raw);
        if (title.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private String buildFileName(String title, String format) {
        String safe = title.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        safe = safe.replaceAll("[.\\s]+$", "");
        if (safe.isEmpty()) {
            safe = DEFAULT_TITLE;
        }
        if (safe.length() > MAX_FILE_TITLE_LENGTH) {
            safe = safe.substring(0, MAX_FILE_TITLE_LENGTH);
        }
        return safe + "_" + FILE_TIME.format(LocalDateTime.now()) + "." + format;
    }

    private String describe(AiDataExportRequest.Query query) {
        return query == null ? "空查询" : StringUtils.defaultIfBlank(query.getKey(), query.getInterfaceCode());
    }

    private Map<String, Object> detail(List<QueryStat> stats) {
        Map<String, Object> detail = new LinkedHashMap<>();
        List<Map<String, Object>> queries = new ArrayList<>();
        for (QueryStat stat : stats) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", stat.key());
            item.put("interfaceCode", stat.interfaceCode());
            item.put("rows", stat.rows());
            item.put("pages", stat.pages());
            item.put("complete", stat.complete());
            if (stat.reason() != null) {
                item.put("reason", stat.reason());
            }
            queries.add(item);
        }
        detail.put("queries", queries);
        return detail;
    }

    private Map<String, Object> auditSummary(AiDataExportRequest request, List<QueryStat> stats, long rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("format", StringUtils.defaultIfBlank(request.getFormat(), "xlsx"));
        summary.put("title", request.getTitle());
        summary.put("rows", rows);
        summary.put("queries", detail(stats).get("queries"));
        return summary;
    }

    private ExportOutcome failure(String error, String message, List<QueryStat> stats,
                                  Map<String, Object> detail) {
        return new ExportOutcome(false, error, message, null, 0,
                stats == null ? List.of() : stats, detail == null ? Map.of() : detail);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("导出临时文件清理失败 path={} error={}", path, e.getMessage());
        }
    }
}
