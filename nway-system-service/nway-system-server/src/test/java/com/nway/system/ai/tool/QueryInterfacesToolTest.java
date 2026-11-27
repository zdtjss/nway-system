package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.service.AiDataSize;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiInterfaceCallService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiLimits;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("批量只读查询：多调用、部分失败、写接口拒绝与展示裁剪")
class QueryInterfacesToolTest {

    private AiInterfaceCallService callService;
    private AiInterfaceService interfaceService;
    private QueryInterfacesTool tool;
    private AiUserContext user;

    @BeforeEach
    void setUp() {
        callService = mock(AiInterfaceCallService.class);
        interfaceService = mock(AiInterfaceService.class);
        tool = new QueryInterfacesTool(callService, interfaceService, new AiDataSize(), new AiLimits());
        user = new AiUserContext();
        user.setEmployeeId("emp-1");
        user.setTenantCode("default");
        when(interfaceService.getPublishedByCode(any(), anyString())).thenAnswer(invocation -> {
            AiInterfaceConfig config = new AiInterfaceConfig();
            config.setInterfaceCode(invocation.getArgument(1));
            config.setName("接口" + invocation.getArgument(1));
            config.setRiskLevel("READ");
            return config;
        });
    }

    @Test
    @DisplayName("一次工具调用执行多个只读接口")
    void executesMultipleCalls() {
        when(callService.call(any(), eq("hr.leave.page"), any(), eq(false), anyString(), anyString(), anyString()))
                .thenReturn(success("hr.leave.page", "请假", rows(2)));
        when(callService.call(any(), eq("hr.overtime.page"), any(), eq(false), anyString(), anyString(), anyString()))
                .thenReturn(success("hr.overtime.page", "加班", rows(3)));

        JSONObject body = invoke(List.of(
                call("hr.leave.page", "请假"),
                call("hr.overtime.page", "加班")));

        assertThat(body.getBooleanValue("success")).isTrue();
        assertThat(body.getJSONArray("results")).hasSize(2);
        assertThat(body.getJSONArray("results").getJSONObject(0).getBooleanValue("success")).isTrue();
        assertThat(body.getJSONArray("results").getJSONObject(0).getIntValue("rowCount")).isEqualTo(2);
    }

    @Test
    @DisplayName("单个调用失败不影响其他结果")
    void partialFailureDoesNotBreakBatch() {
        when(callService.call(any(), eq("hr.leave.page"), any(), eq(false), anyString(), anyString(), anyString()))
                .thenThrow(new AiInterfaceCallService.CallException(AiErrors.PERMISSION_DENIED, "当前用户无权调用"));
        when(callService.call(any(), eq("hr.overtime.page"), any(), eq(false), anyString(), anyString(), anyString()))
                .thenReturn(success("hr.overtime.page", "加班", rows(1)));

        JSONObject body = invoke(List.of(
                call("hr.leave.page", "请假"),
                call("hr.overtime.page", "加班")));

        JSONObject failed = body.getJSONArray("results").getJSONObject(0);
        JSONObject ok = body.getJSONArray("results").getJSONObject(1);
        assertThat(failed.getBooleanValue("success")).isFalse();
        assertThat(failed.getString("error")).isEqualTo(AiErrors.PERMISSION_DENIED);
        assertThat(ok.getBooleanValue("success")).isTrue();
    }

    @Test
    @DisplayName("任一调用为写接口时整批拒绝")
    void rejectsWriteInterface() {
        when(interfaceService.getPublishedByCode(any(), eq("hr.leave.write"))).thenAnswer(invocation -> {
            AiInterfaceConfig config = new AiInterfaceConfig();
            config.setInterfaceCode("hr.leave.write");
            config.setName("提交请假");
            config.setRiskLevel("WRITE");
            return config;
        });

        JSONObject body = invoke(List.of(
                call("hr.leave.page", "请假"),
                call("hr.leave.write", "提交")));

        assertThat(body.getBooleanValue("success")).isFalse();
        assertThat(body.getString("error")).isEqualTo(AiErrors.PERMISSION_DENIED);
        verify(callService, never()).call(any(), anyString(), any(), eq(false), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("超过展示上限时截取前 N 行并标记 truncated")
    void trimsDisplayData() {
        when(callService.call(any(), eq("hr.leave.page"), any(), eq(false), anyString(), anyString(), anyString()))
                .thenReturn(success("hr.leave.page", "请假", rows(300)));

        JSONObject body = invoke(List.of(call("hr.leave.page", "请假")));

        JSONObject result = body.getJSONArray("results").getJSONObject(0);
        assertThat(result.getBooleanValue("truncated")).isTrue();
        assertThat(result.getIntValue("rowCount")).isEqualTo(300);
        assertThat(result.getJSONObject("data").getJSONArray("records")).hasSize(200);
    }

    private JSONObject invoke(List<Map<String, Object>> calls) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("calls", calls);
        RuntimeContext context = RuntimeContext.builder()
                .sessionId("session-1")
                .put(AiUserContext.class, user)
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .input(input)
                .runtimeContext(context)
                .build();
        ToolResultBlock result = tool.callAsync(param).block();
        assertThat(result).isNotNull();
        String text = ((TextBlock) result.getOutput().get(0)).getText();
        return JSON.parseObject(text);
    }

    private Map<String, Object> call(String interfaceCode, String label) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("interfaceCode", interfaceCode);
        call.put("label", label);
        call.put("args", Map.of());
        return call;
    }

    private List<Map<String, Object>> rows(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> (Map<String, Object>) Map.<String, Object>of("id", i))
                .toList();
    }

    private AiInterfaceCallService.CallResult success(String code, String name, List<Map<String, Object>> records) {
        AiInvokeResult result = new AiInvokeResult();
        result.setSuccess(true);
        result.setInterfaceCode(code);
        result.setData(Map.of("records", records, "total", records.size()));
        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode(code);
        config.setName(name);
        config.setRiskLevel("READ");
        return new AiInterfaceCallService.CallResult(true, code, name, config,
                null, null, result, 10);
    }
}
