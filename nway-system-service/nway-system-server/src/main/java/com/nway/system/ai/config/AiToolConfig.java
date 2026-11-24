package com.nway.system.ai.config;

import com.nway.system.ai.service.AiArgumentValidator;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiChatFileService;
import com.nway.system.ai.service.AiInterfaceSearchService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiInvoker;
import com.nway.system.ai.service.AiOptionService;
import com.nway.system.ai.service.AiPermissionService;
import com.nway.system.ai.service.AiSchemaCompiler;
import com.nway.system.ai.tool.GetInterfaceConfigTool;
import com.nway.system.ai.tool.InvokeInterfaceTool;
import com.nway.system.ai.tool.LookupOptionsTool;
import com.nway.system.ai.tool.QueryInterfaceTool;
import com.nway.system.ai.tool.ReadFileTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具注册：核心工具 3 个（查询直返、通用调用、接口检索），不创建工具组，不注册 reset_tools。
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
     * 只读查询直返：结果直接作为本轮答复，不再让模型转述。
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

    @Bean
    public InvokeInterfaceTool invokeInterfaceTool(AiInterfaceService interfaceService,
                                                   AiArgumentValidator validator,
                                                   AiInvoker invoker,
                                                   AiAuditService auditService,
                                                   AiPermissionService permissionService,
                                                   AiChatFileService chatFileService) {
        return new InvokeInterfaceTool(interfaceService, validator, invoker, auditService,
                permissionService, chatFileService);
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
}
