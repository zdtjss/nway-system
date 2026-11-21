package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nway.system.ai.service.AiInterfaceSearchService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiPermissionService;
import com.nway.system.ai.service.AiSchemaCompiler;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GetInterfaceConfigTool：原话检索与低置信提示")
class GetInterfaceConfigToolTest {

    private AiInterfaceSearchService searchService;
    private GetInterfaceConfigTool tool;
    private AiUserContext user;

    @BeforeEach
    void setUp() {
        searchService = mock(AiInterfaceSearchService.class);
        tool = new GetInterfaceConfigTool(
                mock(AiInterfaceService.class),
                searchService,
                mock(AiPermissionService.class),
                mock(AiSchemaCompiler.class));
        user = new AiUserContext();
        user.setEmployeeId("admin");
        user.setTenantCode("default");
        user.setCurrentMessage("我有待办事项吗");
    }

    @Test
    @DisplayName("模型改写 query 时仍用用户原话一起检索")
    void searchesWithCurrentUserMessage() {
        when(searchService.search(any(), anyString(), any(), anyInt()))
                .thenReturn(List.of(new AiInterfaceSearchService.ScoredConfig(interfaceConfig(), 0.41)));

        invoke("list pending tasks for current user");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(searchService).search(any(), captor.capture(), any(), anyInt());
        assertThat(captor.getValue())
                .contains("我有待办事项吗")
                .contains("list pending tasks for current user");
    }

    @Test
    @DisplayName("低于置信阈值时禁止模型直接调用接口")
    void marksLowConfidence() {
        when(searchService.search(any(), anyString(), any(), anyInt()))
                .thenReturn(List.of(new AiInterfaceSearchService.ScoredConfig(interfaceConfig(), 0.41)));

        JSONObject body = invoke("list pending tasks for current user");

        assertThat(body.getBooleanValue("lowConfidence")).isTrue();
        assertThat(body.getString("next")).contains("不要调用");
    }

    @Test
    @DisplayName("高于置信阈值时保留正常调用引导")
    void keepsNormalNextWhenConfident() {
        when(searchService.search(any(), anyString(), any(), anyInt()))
                .thenReturn(List.of(new AiInterfaceSearchService.ScoredConfig(interfaceConfig(), 0.80)));

        JSONObject body = invoke("我的待办");

        assertThat(body.containsKey("lowConfidence")).isFalse();
        assertThat(body.getString("next")).contains("调用 invoke_interface");
    }

    private JSONObject invoke(String query) {
        RuntimeContext context = RuntimeContext.builder()
                .sessionId("session-1")
                .put(AiUserContext.class, user)
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .input(Map.of("query", query))
                .runtimeContext(context)
                .build();
        ToolResultBlock result = tool.callAsync(param).block();
        assertThat(result).isNotNull();
        String text = ((TextBlock) result.getOutput().get(0)).getText();
        return JSON.parseObject(text);
    }

    private AiInterfaceConfig interfaceConfig() {
        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode("nway_system_user_query_page");
        config.setName("nway人员列表");
        config.setDescription("批量获取nway人员信息");
        return config;
    }
}
