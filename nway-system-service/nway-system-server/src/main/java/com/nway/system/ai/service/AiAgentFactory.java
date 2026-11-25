package com.nway.system.ai.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.tool.ExportAnalysisTool;
import com.nway.system.ai.tool.GetInterfaceConfigTool;
import com.nway.system.ai.tool.InvokeInterfaceTool;
import com.nway.system.ai.tool.LookupOptionsTool;
import com.nway.system.ai.tool.QueryInterfaceTool;
import com.nway.system.ai.tool.ReadFileTool;
import com.nway.system.ai.entity.AiModel;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * HarnessAgent 工厂：按「模型 + 系统提示词」缓存 Agent。
 * <p>
 * Agent 本身无会话状态（会话在 Redis），可跨用户复用；提示词变化即换缓存键。
 * 核心工具 4 个：get_interface_config / query_interface（查询直返）/ invoke_interface / export_analysis。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiAgentFactory {

    public static final String AGENT_ID = "nway-assistant";

    private static final int MAX_ITERS = 12;

    private final AgentStateStore stateStore;
    private final AiModelService modelService;
    private final GetInterfaceConfigTool getInterfaceConfigTool;
    private final QueryInterfaceTool queryInterfaceTool;
    private final InvokeInterfaceTool invokeInterfaceTool;
    private final LookupOptionsTool lookupOptionsTool;
    private final ReadFileTool readFileTool;
    private final ExportAnalysisTool exportAnalysisTool;
    private final boolean lookupOptionsEnabled;
    private final boolean readFileEnabled;
    private final Path workspace;
    private final DistributedStore distributedStore;

    private final com.github.benmanes.caffeine.cache.Cache<String, HarnessAgent> agentCache =
            Caffeine.newBuilder().maximumSize(16).expireAfterAccess(Duration.ofMinutes(30)).build();

    public AiAgentFactory(AgentStateStore stateStore, AiModelService modelService,
                          GetInterfaceConfigTool getInterfaceConfigTool,
                          @Qualifier("queryInterfaceTool") QueryInterfaceTool queryInterfaceTool,
                          @Qualifier("invokeInterfaceTool") InvokeInterfaceTool invokeInterfaceTool,
                          LookupOptionsTool lookupOptionsTool,
                          ReadFileTool readFileTool,
                          ExportAnalysisTool exportAnalysisTool,
                          DistributedStore distributedStore,
                          @Value("${nway.ai.tools.lookup-options-enabled:false}") boolean lookupOptionsEnabled,
                          @Value("${nway.ai.tools.read-file-enabled:false}") boolean readFileEnabled,
                          @Value("${nway.ai.workspace:./data/ai-workspace}") String workspaceDir) {
        this.stateStore = stateStore;
        this.modelService = modelService;
        this.getInterfaceConfigTool = getInterfaceConfigTool;
        this.queryInterfaceTool = queryInterfaceTool;
        this.invokeInterfaceTool = invokeInterfaceTool;
        this.lookupOptionsTool = lookupOptionsTool;
        this.readFileTool = readFileTool;
        this.exportAnalysisTool = exportAnalysisTool;
        this.lookupOptionsEnabled = lookupOptionsEnabled;
        this.readFileEnabled = readFileEnabled;
        this.workspace = Path.of(workspaceDir).toAbsolutePath().normalize();
        this.distributedStore = distributedStore;
        try {
            Files.createDirectories(workspace);
        } catch (Exception e) {
            throw new IllegalStateException("创建 AI 工作目录失败：" + workspace, e);
        }
        // 会话消息正文存放在该目录，固定为绝对路径便于排查「历史记录读不到」类问题
        log.info("AI 工作目录：{}", workspace);
    }

    public HarnessAgent getAgent(AiUserContext user, String systemPrompt) {
        return getAgent(user, systemPrompt, null);
    }

    /**
     * 按用户选择的模型构建 Agent；modelId 为空时使用租户默认模型。
     */
    public HarnessAgent getAgent(AiUserContext user, String systemPrompt, String modelId) {
        AiModel modelEntity = modelService.getChatModel(user.effectiveTenantCode(), modelId);
        String prompt = systemPrompt + optionalToolHint();
        String promptHash = sha256(prompt);
        String key = modelEntity.getId() + ":" + modelEntity.getModifiedTime() + ":" + promptHash;
        HarnessAgent cached = agentCache.getIfPresent(key);
        if (cached != null) {
            log.debug("复用 AI Agent 缓存 model={} promptHash={}", modelEntity.getModelName(),
                    promptHash.substring(0, 8));
            return cached;
        }
        log.info("AI Agent 缓存未命中 model={} modelId={} promptHash={} tenant={}",
                modelEntity.getModelName(), modelEntity.getId(), promptHash.substring(0, 8),
                user.effectiveTenantCode());
        return agentCache.get(key, k -> build(prompt, modelEntity));
    }

    /**
     * 本轮对话模型单次输出上限：与 buildModel 的 maxTokens 取值一致，用于识别回答是否被截断。
     */
    public int maxOutputTokens(AiUserContext user, String modelId) {
        return modelService.effectiveMaxTokens(
                modelService.getChatModel(user.effectiveTenantCode(), modelId));
    }

    private String optionalToolHint() {
        StringBuilder sb = new StringBuilder();
        if (lookupOptionsEnabled) {
            sb.append("\n12. 字段带 optionSource 或候选值过多时，可用 lookup_options(interfaceCode, fieldKey, keyword) 查询候选值。");
        }
        if (readFileEnabled) {
            sb.append("\n13. 附件：图片已随消息直接提供，可直接分析；文档用 read_file(fileId) 按需读取；"
                    + "纯粹的文件内容分析不要调用业务接口，需要读文件再调接口时先 read_file 提取信息。");
        }
        return sb.toString();
    }

    private HarnessAgent build(String systemPrompt, AiModel modelEntity) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(getInterfaceConfigTool);
        toolkit.registerAgentTool(queryInterfaceTool);
        toolkit.registerAgentTool(invokeInterfaceTool);
        toolkit.registerAgentTool(exportAnalysisTool);
        if (lookupOptionsEnabled) {
            toolkit.registerAgentTool(lookupOptionsTool);
        }
        if (readFileEnabled) {
            toolkit.registerAgentTool(readFileTool);
        }

        HarnessAgent agent = HarnessAgent.builder()
                .name(AGENT_ID)
                .agentId(AGENT_ID)
                .description("nway 办公智能体：理解提问、附件与当前页面，基于已接入的业务接口完成查询与办理；提交前需用户确认。")
                .sysPrompt(systemPrompt)
                .model(modelService.buildModel(modelEntity))
                .toolkit(toolkit)
                .stateStore(stateStore)
                .distributedStore(distributedStore)            // 新增：自动注入 baseStore
                .filesystem(new RemoteFilesystemSpec()         // 新增：会话/内存路由到数据库
                        .isolationScope(IsolationScope.USER)
                        .anonymousUserId("_default"))
                .workspace(workspace)
                .maxIters(MAX_ITERS)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.DEFAULT)
                        .build())
                .disableFilesystemTools()
                .disableShellTool()
                .disableWebTools()
                .disableMemoryTools()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableWorkspaceContext()
                .disableToolsConfig()
                .stopOnReject(true)
                .build();
        log.info("构建 AI Agent：model={} modelId={} promptHash={} tools={} maxIters={}",
                modelEntity.getModelName(), modelEntity.getId(), sha256(systemPrompt).substring(0, 8),
                toolkit.getToolNames(), MAX_ITERS);
        return agent;
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
