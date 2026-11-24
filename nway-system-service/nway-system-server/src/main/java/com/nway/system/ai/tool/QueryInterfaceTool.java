package com.nway.system.ai.tool;

import com.nway.system.ai.service.AiArgumentValidator;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiChatFileService;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiInvoker;
import com.nway.system.ai.service.AiPermissionService;

/**
 * 工具：只读查询直返。
 * <p>
 * 复用 {@link InvokeInterfaceTool} 的参数校验、鉴权与调用逻辑，但声明 returnDirect：
 * 查询成功后接口结果直接作为本轮答复展示给用户，不再产生一次大模型转述调用。
 * 需要分析、多次调用或写操作时仍使用 invoke_interface。
 *
 * @author nway
 */
public class QueryInterfaceTool extends InvokeInterfaceTool {

    public static final String NAME = "query_interface";

    private static final String DESCRIPTION = """
            直接查询只读业务接口，并把结果作为本轮答复展示给用户；这是接口查询的默认工具。
            结果直接展示，不做统计汇总；不要为了汇总、统计接口结果而改用 invoke_interface。
            适用：一次查询即可完成，用户只想查看/获取数据，不需要分析，也不需要基于结果继续调用其他接口。
            只读接口专属；写操作请使用 invoke_interface（会触发用户确认）。需要多次调用接口时不要使用本工具。
            args 必须严格使用 get_interface_config 返回的 inputSchema 字段。
            如果缺少必填信息，先向用户追问，不要猜测。
            调用成功后本轮直接结束，结果不会再返回给你继续推理。
            """;

    public QueryInterfaceTool(AiInterfaceService interfaceService, AiArgumentValidator validator,
                              AiInvoker invoker, AiAuditService auditService,
                              AiPermissionService permissionService,
                              AiChatFileService chatFileService) {
        super(NAME, DESCRIPTION, true, interfaceService, validator, invoker, auditService,
                permissionService, chatFileService);
    }
}
