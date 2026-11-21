package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口检索：中文自然语言意图（无空格）必须能命中接口名称/描述；
 * 规则词面无法命中时，回退大模型语义路由。
 */
@DisplayName("AiInterfaceSearchService：中文意图检索与大模型路由")
class AiInterfaceSearchServiceTest {

    private AiInterfaceService interfaceService;
    private AiPermissionService permissionService;
    private AiIntentRouter intentRouter;
    private AiInterfaceSearchService searchService;
    private AiUserContext user;

    @BeforeEach
    void setUp() {
        interfaceService = mock(AiInterfaceService.class);
        permissionService = mock(AiPermissionService.class);
        intentRouter = mock(AiIntentRouter.class);
        searchService = new AiInterfaceSearchService(interfaceService, permissionService, intentRouter);
        user = new AiUserContext();
        user.setEmployeeId("admin");
        user.setTenantCode("default");
        when(interfaceService.getPublishedConfigs("default"))
                .thenReturn(List.of(userListInterface(), mockSubmitInterface(), myTaskInterface()));
        when(permissionService.isInterfaceAllowed(any(), any(AiInterfaceConfig.class))).thenReturn(true);
        when(intentRouter.route(any(), anyString(), anyList(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("「查询人员列表信息」命中 nway 人员列表接口")
    void matchesUserListByNaturalLanguage() {
        assertFirst("查询人员列表信息", "nway_system_user_query_page");
    }

    @Test
    @DisplayName("「获取员工名单」按近义词命中人员列表接口")
    void matchesUserListBySynonyms() {
        assertFirst("获取员工名单", "nway_system_user_query_page");
    }

    @Test
    @DisplayName("「获取员工信息列表」按近义词命中人员列表接口")
    void matchesUserListWithFillerWords() {
        assertFirst("获取员工信息列表", "nway_system_user_query_page");
    }

    @Test
    @DisplayName("「提交访厂申请」仍优先命中访厂接口，不被中文兜底带偏")
    void stillMatchesSubmitInterface() {
        assertFirst("提交访厂申请", "ai_mock_submit");
    }

    @Test
    @DisplayName("「我有待办事项吗」命中我的待办接口")
    void matchesMyTaskByChinese() {
        assertFirst("我有待办事项吗", "bpm_my_task");
    }

    @Test
    @DisplayName("英文待办意图按同义词命中我的待办接口")
    void matchesMyTaskByEnglish() {
        assertFirst("list pending tasks for current user", "bpm_my_task");
    }

    @Test
    @DisplayName("英文人员意图仍命中人员列表接口")
    void matchesUserListByEnglish() {
        assertFirst("list employees", "nway_system_user_query_page");
    }

    @Test
    @DisplayName("规则检索置信度高时不调用大模型")
    void skipsLlmWhenLexicalConfident() {
        assertFirst("查询人员列表信息", "nway_system_user_query_page");
        verify(intentRouter, never()).route(any(), anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("词面无明显命中时用大模型语义路由补召回")
    void fallsBackToLlmRouting() {
        when(intentRouter.route(any(), anyString(), anyList(), anyInt()))
                .thenReturn(List.of("ai_mock_submit"));
        List<AiInterfaceSearchService.ScoredConfig> result =
                searchService.search(user, "我要办个进厂参观的事", null, 5);
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).config().getInterfaceCode()).isEqualTo("ai_mock_submit");
        assertThat(result.get(0).score()).isBetween(0.9, 1.0);
    }

    @Test
    @DisplayName("大模型返回目录外的编码时忽略，防止幻觉绕过权限")
    void ignoresUnknownCodeFromLlm() {
        when(intentRouter.route(any(), anyString(), anyList(), anyInt()))
                .thenReturn(List.of("not_exist_interface"));
        assertThat(searchService.search(user, "今天天气怎么样", null, 5)).isEmpty();
    }

    @Test
    @DisplayName("无关意图不返回候选")
    void ignoresUnrelatedQuery() {
        assertThat(searchService.search(user, "今天天气怎么样", null, 5)).isEmpty();
    }

    @Test
    @DisplayName("空查询不返回候选，避免误导模型")
    void ignoresBlankQuery() {
        assertThat(searchService.search(user, "  ", null, 5)).isEmpty();
    }

    @Test
    @DisplayName("所有候选分值都归一化到 0~1")
    void keepsScoresNormalized() {
        List<AiInterfaceSearchService.ScoredConfig> result =
                searchService.search(user, "提交访厂申请", null, 5);
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(scored ->
                assertThat(scored.score()).isBetween(0.0, 1.0));
    }

    private void assertFirst(String query, String expectedCode) {
        List<AiInterfaceSearchService.ScoredConfig> result = searchService.search(user, query, null, 5);
        assertThat(result).as("query=%s", query).isNotEmpty();
        assertThat(result.get(0).config().getInterfaceCode())
                .as("query=%s", query).isEqualTo(expectedCode);
    }

    private AiInterfaceConfig userListInterface() {
        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode("nway_system_user_query_page");
        config.setName("nway人员列表");
        config.setDescription("批量获取nway人员信息");
        config.setAppCode("nway-system");
        config.setPath("/user/queryPage");
        config.setInputSchema(List.of(
                field("employeeId", "工号"),
                field("realName", "realName"),
                field("status", "status"),
                field("current", "current"),
                field("size", "size")));
        return config;
    }

    private AiInterfaceConfig mockSubmitInterface() {
        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode("ai_mock_submit");
        config.setName("Mock 访厂申请");
        config.setDescription("提交访厂体验申请（写操作，需用户确认）");
        config.setAppCode("bpm");
        config.setPath("/mock/submit");
        config.setRiskLevel("WRITE");
        config.setInputSchema(List.of(
                field("title", "申请标题"),
                field("visitDate", "参观日期"),
                field("visitorCount", "来访人数"),
                field("visitType", "参观类型")));
        return config;
    }

    private AiInterfaceConfig myTaskInterface() {
        AiInterfaceConfig config = new AiInterfaceConfig();
        config.setInterfaceCode("bpm_my_task");
        config.setName("我的待办");
        config.setDescription("查询当前登录人的待办审批任务列表，支持流程单号过滤与分页");
        config.setAppCode("bpm");
        config.setPath("/basBpmMyTask/getMyTask");
        config.setRiskLevel("READ");
        config.setInputSchema(List.of(
                field("currentPage", "当前页"),
                field("pageSize", "每页条数"),
                field("reqno", "流程单号"),
                field("empuid", "待办人工号")));
        return config;
    }

    private AiFieldDef field(String key, String label) {
        AiFieldDef field = new AiFieldDef();
        field.setKey(key);
        field.setLabel(label);
        field.setType("STRING");
        return field;
    }
}
