package com.nway.system.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.ai.service.AiArgumentValidator;
import com.nway.system.ai.service.AiAuditService;
import com.nway.system.ai.service.AiChatFileService;
import com.nway.system.ai.service.AiErrors;
import com.nway.system.ai.service.AiInterfaceService;
import com.nway.system.ai.service.AiInvoker;
import com.nway.system.ai.service.AiPermissionService;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.ToolCallParam;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

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
            直接查询单个只读业务接口，并把结果作为本轮答复展示给用户。
            新对话优先使用 query_interfaces（支持一次提交多个只读查询）；本工具保留为单次查询的兼容入口。
            结果直接展示，不做统计汇总；不要为了汇总、统计接口结果而改用 invoke_interface。
            适用：一次查询即可完成，用户只想查看/获取数据，不需要分析，也不需要基于结果继续调用其他接口，且本轮不需要导出文件。
            不适用：用户要求导出、下载、保存结果或生成报告/表格文件；需要多次调用接口或写操作的场景。
            只读接口专属；写操作请使用 invoke_interface（会触发用户确认）。
            args 必须严格使用 search_interfaces 返回的 inputSchema 字段。
            如果缺少必填信息，先向用户追问，不要猜测。
            调用成功后本轮直接结束，结果不会再返回给你继续推理。
            """;

    /**
     * 导出意图兜底：命中时拒绝直返查询。
     * <p>
     * query_interface 成功即结束本轮，模型没有机会再调用 export_analysis 生成文件。
     * 返回错误结果不会触发 returnDirect 短路，模型可据此改用 invoke_interface 取数后再导出。
     */
    private static final Pattern EXPORT_INTENT = Pattern.compile(
            "导出|下载|另存为|保存为|输出为|转成|转换成|生成.{0,8}(文件|报告|表格|excel|word|ppt|html|pdf)",
            Pattern.CASE_INSENSITIVE);

    public QueryInterfaceTool(AiInterfaceService interfaceService, AiArgumentValidator validator,
                              AiInvoker invoker, AiAuditService auditService,
                              AiPermissionService permissionService,
                              AiChatFileService chatFileService) {
        super(NAME, DESCRIPTION, true, interfaceService, validator, invoker, auditService,
                permissionService, chatFileService);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        AiUserContext user = param.getRuntimeContext() == null
                ? null : param.getRuntimeContext().get(AiUserContext.class);
        if (user != null && StringUtils.isNotBlank(user.getCurrentMessage())
                && EXPORT_INTENT.matcher(user.getCurrentMessage()).find()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", AiErrors.INVALID_ARGUMENT);
            body.put("message", "本轮用户要求导出文件，query_interface 会直接结束本轮导致无法生成文件；"
                    + "请改用 export_query_data 直接导出，或使用 export_tool_result / export_conversation");
            return Mono.just(ToolResultBlock.text(JSON.toJSONString(body))
                    .withState(ToolResultState.ERROR));
        }
        return super.callAsync(param);
    }
}
