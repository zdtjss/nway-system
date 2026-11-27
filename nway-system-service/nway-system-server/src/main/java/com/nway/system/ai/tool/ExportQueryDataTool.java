package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.service.AiDataExportService;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiLogs;
import com.nway.system.ai.web.model.AiDataExportRequest;
import com.nway.system.ai.web.model.AiExportVo;
import com.nway.system.common.tenant.TenantUtil;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具：大数据确定性导出。
 * <p>
 * 模型只输出查询计划（接口、参数、分页、格式），服务端分页取全量并直接写文件；
 * 模型只能看到行数、页数、完整性等元数据，看不到数据行，也不能基于数据编造结论。
 *
 * @author nway
 */
@Slf4j
public class ExportQueryDataTool extends ToolBase {

    public static final String NAME = "export_query_data";

    private static final String DESCRIPTION = """
            按查询计划导出业务数据为文件（大数据导出/导出原始数据的首选工具）。
            模型只提供查询计划：接口、参数、分页与格式；不要传入或复述数据行，不要做统计分析。
            适用：用户要求导出、下载、保存接口数据，或数据量明显超过在线分析上限（明细、全量、全部记录）。
            不适用：小数据分析报告 → export_analysis；导出当前对话 → export_conversation；导出已有查询结果 → export_tool_result。
            只读接口专属；计划中出现写接口会被整体拒绝。
            完整性：服务端按分页取全量并核对 total；接口不支持分页、单页超限或记录数不一致时不会生成文件，只返回 handoff。
            args 必须严格使用 search_interfaces 返回的 inputSchema 字段；分页参数不确定时不要手写，由服务端按接口定义识别。
            默认格式 xlsx（每个查询一个 sheet）；csv/html 适合原始数据；docx 仅用于小数据报告。
            生成成功后只需简短告知用户文件已生成，下载链接由界面展示，不要粘贴数据。
            """;

    private final AiDataExportService exportService;

    public ExportQueryDataTool(AiDataExportService exportService) {
        super(ToolBase.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema())
                .readOnly(true));
        this.exportService = exportService;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock execute(ToolCallParam param) {
        long start = System.currentTimeMillis();
        String sessionId = AiLogs.sessionId(param);
        String toolCallId = AiLogs.toolCallId(param);
        AiUserContext user = param.getRuntimeContext() == null
                ? null : param.getRuntimeContext().get(AiUserContext.class);
        if (user == null) {
            return error(AiErrors.USER_CONTEXT_MISSING, "缺少用户上下文");
        }
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        AiDataExportRequest request;
        try {
            request = JSON.parseObject(JSON.toJSONString(input), AiDataExportRequest.class);
        } catch (Exception e) {
            return error(AiErrors.INVALID_ARGUMENT, "查询计划格式不正确：" + e.getMessage());
        }
        if (request == null || request.getQueries() == null || request.getQueries().isEmpty()) {
            return error(AiErrors.INVALID_ARGUMENT, "queries 不能为空");
        }

        AiDataExportService.ExportOutcome outcome;
        String tenant = user.effectiveTenantCode();
        if (StringUtils.isBlank(tenant)) {
            outcome = exportService.export(user, request);
        } else {
            Object[] holder = new Object[1];
            // 工具在 reactor 线程执行，需显式恢复租户上下文，文件才能写入用户租户
            TenantUtil.runAsTenant(tenant, () -> holder[0] = exportService.export(user, request));
            outcome = (AiDataExportService.ExportOutcome) holder[0];
        }

        if (!outcome.success()) {
            log.warn("工具调用失败 tool={} session={} toolCallId={} error={} message={} elapsed={}ms",
                    NAME, sessionId, toolCallId, outcome.error(), outcome.message(),
                    System.currentTimeMillis() - start);
            // 只有“数据无法保证完整/超限”才降级为 handoff；参数、权限错误交给模型修正
            if (AiErrors.DATA_INCOMPLETE.equals(outcome.error())
                    || AiErrors.EXPORT_LIMIT_EXCEEDED.equals(outcome.error())
                    || AiErrors.DATA_TOO_LARGE.equals(outcome.error())) {
                return handoff(outcome);
            }
            return error(outcome.error(), outcome.message());
        }
        AiExportVo vo = outcome.file();
        log.info("工具调用完成 tool={} session={} toolCallId={} format={} fileId={} rows={} queries={} "
                        + "elapsed={}ms", NAME, sessionId, toolCallId, request.getFormat(), vo.getFileId(),
                outcome.rows(), outcome.queries().size(), System.currentTimeMillis() - start);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("fileId", vo.getFileId());
        body.put("fileName", vo.getFileName());
        body.put("size", vo.getSize());
        body.put("format", StringUtils.defaultIfBlank(request.getFormat(), "xlsx"));
        body.put("url", vo.getUrl());
        body.put("rows", outcome.rows());
        body.put("queries", queryStats(outcome));
        body.put("message", "文件已生成，点击对话中的文件名即可下载");
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private ToolResultBlock handoff(AiDataExportService.ExportOutcome outcome) {
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("type", "MENU_OR_ADMIN");
        handoff.put("message", outcome.message());
        handoff.put("suggestedAction", "MENU_EXPORT");
        handoff.put("queries", outcome.detail() == null ? List.of() : outcome.detail().get("queries"));
        handoff.put("options", List.of(
                Map.of("action", "NARROW_SCOPE", "label", "缩小查询范围"),
                Map.of("action", "MENU_EXPORT", "label", "通过业务菜单导出")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", outcome.error());
        body.put("message", outcome.message());
        body.put("queries", outcome.detail() == null ? List.of() : outcome.detail().get("queries"));
        body.put("handoff", handoff);
        return ToolResultBlock.text(JSON.toJSONString(body));
    }

    private List<Map<String, Object>> queryStats(AiDataExportService.ExportOutcome outcome) {
        List<Map<String, Object>> queries = new ArrayList<>();
        for (AiDataExportService.QueryStat stat : outcome.queries()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", stat.key());
            item.put("interfaceCode", stat.interfaceCode());
            item.put("rows", stat.rows());
            item.put("pages", stat.pages());
            item.put("complete", stat.complete());
            queries.add(item);
        }
        return queries;
    }

    private ToolResultBlock error(String code, String message) {
        return ToolResultBlock.text(JSON.toJSONString(Map.of("error", code, "message", message)));
    }

    private static Map<String, Object> inputSchema() {
        Map<String, Object> paginationProperties = new LinkedHashMap<>();
        paginationProperties.put("mode", Map.of(
                "type", "string",
                "enum", List.of("PAGE", "NONE"),
                "description", "PAGE=按页循环；NONE=单次取回；不确定时不要传，由服务端按 inputSchema 识别"));
        paginationProperties.put("pageParam", Map.of(
                "type", "string",
                "description", "页码参数名，如 pageNo；不确定时不传"));
        paginationProperties.put("sizeParam", Map.of(
                "type", "string",
                "description", "每页条数参数名，如 pageSize；不确定时不传"));
        paginationProperties.put("startPage", Map.of(
                "type", "integer",
                "description", "起始页码，默认 1"));
        paginationProperties.put("pageSize", Map.of(
                "type", "integer",
                "description", "每页条数，默认 200"));
        paginationProperties.put("maxPages", Map.of(
                "type", "integer",
                "description", "最大页数，默认取系统上限"));
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("type", "object");
        pagination.put("properties", paginationProperties);

        Map<String, Object> queryProperties = new LinkedHashMap<>();
        queryProperties.put("key", Map.of(
                "type", "string",
                "description", "查询标识，用于返回行数/完整性"));
        queryProperties.put("interfaceCode", Map.of(
                "type", "string",
                "description", "search_interfaces 返回的只读接口编码"));
        queryProperties.put("args", Map.of(
                "type", "object",
                "description", "接口入参，字段由 inputSchema 决定"));
        queryProperties.put("sheetName", Map.of(
                "type", "string",
                "description", "xlsx 的 sheet 名，默认用接口名"));
        queryProperties.put("pagination", pagination);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "object");
        query.put("properties", queryProperties);
        query.put("required", List.of("interfaceCode", "args"));

        Map<String, Object> queries = new LinkedHashMap<>();
        queries.put("type", "array");
        queries.put("description", "要导出的查询计划，至少一个");
        queries.put("items", query);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("format", Map.of(
                "type", "string",
                "enum", List.of("xlsx", "csv", "html", "docx"),
                "description", "导出格式，默认 xlsx；docx 仅用于小数据"));
        properties.put("title", Map.of(
                "type", "string",
                "description", "文件标题，通常取用户问题或会话主题"));
        properties.put("queries", queries);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("queries"));
        return schema;
    }
}
