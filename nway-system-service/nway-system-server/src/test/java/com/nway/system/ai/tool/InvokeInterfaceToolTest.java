package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiInvokeResult;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.domain.AiValidationResult;
import com.nway.system.ai.service.AiArgumentValidator;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiChatFileService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiInvoker;
import com.nway.system.ai.service.AiPermissionService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("接口调用：返回数据保持原样，是否分析由用户问题决定")
class InvokeInterfaceToolTest {

    private AiInterfaceService interfaceService;
    private AiArgumentValidator validator;
    private AiInvoker invoker;
    private AiPermissionService permissionService;
    private AiUserContext user;
    private AiInterfaceConfig config;

    @BeforeEach
    void setUp() {
        interfaceService = mock(AiInterfaceService.class);
        validator = mock(AiArgumentValidator.class);
        invoker = mock(AiInvoker.class);
        permissionService = mock(AiPermissionService.class);

        user = new AiUserContext();
        user.setEmployeeId("admin");
        user.setTenantCode("default");

        config = new AiInterfaceConfig();
        config.setInterfaceCode("order.list");
        config.setName("订单列表");
        config.setRiskLevel("READ");

        when(interfaceService.getPublishedByCode(any(), eq("order.list"))).thenReturn(config);
        when(interfaceService.getAnyPublishedByCode("order.list")).thenReturn(config);
        when(validator.validate(any(), any(), any())).thenReturn(AiValidationResult.ok(Map.of()));
        when(permissionService.evaluate(any(), any())).thenReturn(
                new AiPermissionService.PermissionDecision(true, "ok", "d1",
                        "ai:order.list", "PUBLIC", List.of()));

        AiInvokeResult invokeResult = new AiInvokeResult();
        invokeResult.setSuccess(true);
        invokeResult.setInterfaceCode("order.list");
        invokeResult.setCode(200);
        invokeResult.setMessage("success");
        invokeResult.setData(Map.of(
                "pageNum", 1,
                "summary", Map.of("count", 1, "totalAmount", 5120),
                "list", List.of(Map.of("orderNo", "SO2026010110002"))));
        when(invoker.invoke(any(), any(), any())).thenReturn(invokeResult);
    }

    @Test
    @DisplayName("invoke_interface：接口返回的 summary 等字段原样保留")
    void invokePreservesInterfaceData() {
        JSONObject data = invoke(invokeTool());

        assertThat(data.containsKey("summary")).isTrue();
        assertThat(data.getJSONObject("summary").getIntValue("totalAmount")).isEqualTo(5120);
        assertThat(data.getJSONArray("list")).hasSize(1);
    }

    @Test
    @DisplayName("query_interface：直返结果同样保持接口数据原样")
    void queryPreservesInterfaceData() {
        JSONObject data = invoke(queryTool());

        assertThat(data.containsKey("summary")).isTrue();
        assertThat(data.getJSONArray("list")).hasSize(1);
    }

    @Test
    @DisplayName("只有查询直返工具会跳过后续模型调用")
    void onlyQueryToolReturnsDirect() {
        assertThat(queryTool().isReturnDirect()).isTrue();
        assertThat(invokeTool().isReturnDirect()).isFalse();
    }

    private JSONObject invoke(InvokeInterfaceTool tool) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("interfaceCode", "order.list");
        input.put("args", Map.of());
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
        return JSON.parseObject(text).getJSONObject("data");
    }

    private InvokeInterfaceTool invokeTool() {
        return new InvokeInterfaceTool(interfaceService, validator, invoker,
                mock(AiAuditService.class), permissionService, mock(AiChatFileService.class));
    }

    private QueryInterfaceTool queryTool() {
        return new QueryInterfaceTool(interfaceService, validator, invoker,
                mock(AiAuditService.class), permissionService, mock(AiChatFileService.class));
    }
}
