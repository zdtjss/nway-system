package com.nway.system.ai.config;

import com.nway.system.ai.service.AiArgumentValidator;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiChatFileService;
import com.nway.system.ai.service.AiConversationExportService;
import com.nway.system.ai.service.AiConversationHistoryReader;
import com.nway.system.ai.service.AiDataExportService;
import com.nway.system.ai.service.AiDataSize;
import com.nway.system.ai.service.AiExportService;
import com.nway.system.ai.service.AiInterfaceCallService;
import com.nway.system.ai.service.AiInterfaceSearchService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiInvoker;
import com.nway.system.ai.service.AiLimits;
import com.nway.system.ai.service.AiOptionService;
import com.nway.system.ai.service.AiPermissionService;
import com.nway.system.ai.service.AiSchemaCompiler;
import com.nway.system.ai.tool.ExportAnalysisTool;
import com.nway.system.ai.tool.ExportConversationTool;
import com.nway.system.ai.tool.ExportQueryDataTool;
import com.nway.system.ai.tool.ExportToolResultTool;
import com.nway.system.ai.tool.GetInterfaceConfigTool;
import com.nway.system.ai.tool.InvokeInterfaceTool;
import com.nway.system.ai.tool.LookupOptionsTool;
import com.nway.system.ai.tool.QueryInterfaceTool;
import com.nway.system.ai.tool.QueryInterfacesTool;
import com.nway.system.ai.tool.ReadFileTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具注册：接口检索、只读批量查询、通用调用、确定性导出、会话/结果导出与小数据分析导出。
 * <p>
 * 是否注册批量查询、会话导出由 {@link AiLimits} 的开关控制，关闭后回退到旧的单次查询。
 *
 * @author nway
 */
@Configuration
public class AiToolConfig {

    @Bean
    public GetInterfaceConfigTool getInterfaceConfigTool(AiInterfaceService interfaceService,
                                                         AiInterfaceSearchService searchService,
                                                         AiPermissionService permissionService,
                                                         AiSchemaCompiler schemaCompiler) {
        return new GetInterfaceConfigTool(interfaceService, searchService, permissionService, schemaCompiler);
    }

    /**
     * 单次只读查询直返：批量查询关闭时作为回退入口注册。
     */
    @Bean
    public QueryInterfaceTool queryInterfaceTool(AiInterfaceService interfaceService,
                                                 AiArgumentValidator validator,
                                                 AiInvoker invoker,
                                                 AiAuditService auditService,
                                                 AiPermissionService permissionService,
                                                 AiChatFileService chatFileService) {
        return new QueryInterfaceTool(interfaceService, validator, invoker, auditService,
                permissionService, chatFileService);
    }

    /**
     * 批量只读查询直返：一次工具调用执行多个只读接口。
     */
    @Bean
    public QueryInterfacesTool queryInterfacesTool(AiInterfaceCallService callService,
                                                   AiInterfaceService interfaceService,
                                                   AiDataSize dataSize,
                                                   AiLimits limits) {
        return new QueryInterfacesTool(callService, interfaceService, dataSize, limits);
    }

    @Bean
    public InvokeInterfaceTool invokeInterfaceTool(AiInterfaceService interfaceService,
                                                   AiInterfaceCallService callService,
                                                   AiDataSize dataSize,
                                                   AiLimits limits) {
        return new InvokeInterfaceTool(interfaceService, callService, dataSize, limits);
    }

    /**
     * 可选工具：仅在 nway.ai.tools.lookup-options-enabled=true 时注册给 Agent。
     */
    @Bean
    public LookupOptionsTool lookupOptionsTool(AiInterfaceService interfaceService,
                                               AiOptionService optionService,
                                               AiPermissionService permissionService) {
        return new LookupOptionsTool(interfaceService, optionService, permissionService);
    }

    /**
     * 可选工具：仅在 nway.ai.tools.read-file-enabled=true 时注册给 Agent。
     */
    @Bean
    public ReadFileTool readFileTool(AiChatFileService chatFileService) {
        return new ReadFileTool(chatFileService);
    }

    /**
     * 大数据确定性导出：模型只给查询计划，服务端分页取全量并写文件。
     */
    @Bean
    public ExportQueryDataTool exportQueryDataTool(AiDataExportService dataExportService) {
        return new ExportQueryDataTool(dataExportService);
    }

    /**
     * 会话导出：直接读取 ai_message，不重新分析。
     */
    @Bean
    public ExportConversationTool exportConversationTool(AiConversationExportService exportService) {
        return new ExportConversationTool(exportService);
    }

    /**
     * 已有结果导出：只导出完整且未截断的查询结果。
     */
    @Bean
    public ExportToolResultTool exportToolResultTool(AiConversationHistoryReader historyReader,
                                                     AiConversationExportService exportService) {
        return new ExportToolResultTool(historyReader, exportService);
    }

    /**
     * 小数据分析报告导出：由模型按用户导出意图组织内容。
     */
    @Bean
    public ExportAnalysisTool exportAnalysisTool(AiExportService exportService) {
        return new ExportAnalysisTool(exportService);
    }
}
