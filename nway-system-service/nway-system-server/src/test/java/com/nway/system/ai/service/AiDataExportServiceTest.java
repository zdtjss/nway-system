package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.web.model.AiDataExportRequest;
import com.nway.system.file.domain.FileTicket;
import com.nway.system.file.service.FileService;
import com.nway.system.file.web.model.FileTicketVo;
import com.nway.system.file.web.model.FileVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("大数据确定性导出：分页取全量、total 对账与无分页拒绝")
class AiDataExportServiceTest {

    private AiInterfaceCallService callService;
    private FileService fileService;
    private AiDataExportService service;
    private AiUserContext user;

    @BeforeEach
    void setUp() {
        callService = mock(AiInterfaceCallService.class);
        fileService = mock(FileService.class);
        AiExportService exportService = mock(AiExportService.class);
        AiAuditService auditService = mock(AiAuditService.class);
        service = new AiDataExportService(callService, new AiDataSize(), new AiLimits(),
                fileService, exportService, auditService);

        user = new AiUserContext();
        user.setEmployeeId("emp-1");
        user.setTenantCode("default");

        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode("hr.leave.page");
        config.setName("请假分页");
        config.setRiskLevel("READ");
        when(callService.requireConfig(any(), eq("hr.leave.page"))).thenReturn(config);
        doNothing().when(callService).assertReadable(any(), any());
        when(callService.validateArgs(any(), any(), any())).thenReturn(Map.of());

        FileVo file = new FileVo();
        file.setId("f_1");
        file.setFileName("请假明细.xlsx");
        file.setFileSize(123L);
        when(fileService.saveGenerated(any(InputStream.class), anyLong(), anyString(),
                anyString(), any(), any())).thenReturn(file);
        when(fileService.createTicket(anyString(), anyString()))
                .thenReturn(new FileTicketVo("ticket", "/file/ticketDownload?ticket=ticket", 600));
    }

    @Test
    @DisplayName("按页循环取全量，行数与 total 一致时生成文件")
    void exportsAllPages() {
        when(callService.call(any(), eq("hr.leave.page"), any(), eq(false), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    Map<String, Object> args = invocation.getArgument(2);
                    int page = ((Number) args.get("pageNo")).intValue();
                    List<Map<String, Object>> records = page == 1
                            ? List.of(Map.of("id", 1), Map.of("id", 2))
                            : List.of(Map.of("id", 3), Map.of("id", 4));
                    return success(records, page == 1 ? 4L : 4L);
                });

        AiDataExportService.ExportOutcome outcome = service.export(user, request("PAGE"));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.rows()).isEqualTo(4);
        assertThat(outcome.queries()).hasSize(1);
        assertThat(outcome.queries().get(0).pages()).isEqualTo(2);
        assertThat(outcome.queries().get(0).complete()).isTrue();
        assertThat(outcome.file().getFileId()).isEqualTo("f_1");
        verify(callService, times(2)).call(any(), eq("hr.leave.page"), any(), eq(false),
                anyString(), anyString(), anyString());
        verify(fileService).createTicket(eq("f_1"), eq(FileTicket.PURPOSE_DOWNLOAD));
    }

    @Test
    @DisplayName("接口不支持分页且 total 大于已取行数时拒绝生成文件")
    void rejectsIncompleteSinglePage() {
        when(callService.call(any(), eq("hr.leave.page"), any(), eq(false), anyString(), anyString(), anyString()))
                .thenReturn(success(List.of(Map.of("id", 1), Map.of("id", 2)), 5L));

        AiDataExportService.ExportOutcome outcome = service.export(user, request("NONE"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error()).isEqualTo(AiErrors.DATA_INCOMPLETE);
        assertThat(outcome.file()).isNull();
        assertThat(outcome.queries().get(0).complete()).isFalse();
        verify(fileService, times(0)).saveGenerated(any(InputStream.class), anyLong(), anyString(),
                anyString(), any(), any());
    }

    @Test
    @DisplayName("单页返回被截断时拒绝生成文件")
    void rejectsTruncatedPage() {
        AiInvokeResult truncated = new AiInvokeResult();
        truncated.setSuccess(false);
        truncated.setInterfaceCode("hr.leave.page");
        truncated.setErrorCode(AiErrors.DATA_TOO_LARGE);
        truncated.setMessage("超过单次读取上限");
        truncated.setTruncated(true);
        truncated.setRawBytes(2_000_000);
        when(callService.call(any(), eq("hr.leave.page"), any(), eq(false), anyString(), anyString(), anyString()))
                .thenReturn(new AiInterfaceCallService.CallResult(false, "hr.leave.page", "请假分页",
                        new AiInterfaceConfig(), AiErrors.DATA_TOO_LARGE, truncated.getMessage(),
                        truncated, 10));

        AiDataExportService.ExportOutcome outcome = service.export(user, request("NONE"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.file()).isNull();
        assertThat(outcome.queries().get(0).complete()).isFalse();
    }

    private AiInterfaceCallService.CallResult success(List<Map<String, Object>> records, Long total) {
        AiInvokeResult result = new AiInvokeResult();
        result.setSuccess(true);
        result.setInterfaceCode("hr.leave.page");
        result.setData(Map.of("records", records, "total", total));
        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode("hr.leave.page");
        config.setName("请假分页");
        config.setRiskLevel("READ");
        return new AiInterfaceCallService.CallResult(true, "hr.leave.page", "请假分页", config,
                null, null, result, 10);
    }

    private AiDataExportRequest request(String mode) {
        AiDataExportRequest request = new AiDataExportRequest();
        request.setFormat("xlsx");
        request.setTitle("请假明细");
        AiDataExportRequest.Query query = new AiDataExportRequest.Query();
        query.setKey("leave");
        query.setInterfaceCode("hr.leave.page");
        query.setArgs(Map.of());
        AiDataExportRequest.Pagination pagination = new AiDataExportRequest.Pagination();
        pagination.setMode(mode);
        if ("PAGE".equals(mode)) {
            pagination.setPageParam("pageNo");
            pagination.setSizeParam("pageSize");
            pagination.setPageSize(2);
            pagination.setStartPage(1);
        }
        query.setPagination(pagination);
        request.setQueries(List.of(query));
        return request;
    }
}
