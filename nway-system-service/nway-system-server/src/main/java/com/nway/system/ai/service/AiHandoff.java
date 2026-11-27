package com.nway.system.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化降级（handoff）信封：告诉模型与前端“数据过大或无法保证完整，请走菜单/管理员/缩小范围”。
 * <p>
 * handoff 放在普通工具结果 JSON 中，不新增 SSE 事件；前端渲染为警告卡，
 * 提示词约束模型不得把 handoff 当作数据继续分析或导出。
 *
 * @author nway
 */
public final class AiHandoff {

    public static final String TYPE_MENU_OR_ADMIN = "MENU_OR_ADMIN";

    private AiHandoff() {
    }

    /**
     * 数据体量超过在线分析上限。
     */
    public static Map<String, Object> analysisTooLarge(String interfaceCode, AiDataSize.Size size, AiLimits limits) {
        Map<String, Object> limitsNode = new LinkedHashMap<>();
        limitsNode.put("maxChars", limits.getAnalysisMaxChars());
        limitsNode.put("actualChars", size == null ? 0 : size.chars());
        limitsNode.put("maxRows", limits.getAnalysisMaxRows());
        limitsNode.put("actualRows", size == null ? 0 : size.rows());
        limitsNode.put("maxTotal", limits.getAnalysisMaxTotal());
        limitsNode.put("actualTotal", size == null ? null : size.total());
        return body(AiErrors.DATA_TOO_LARGE,
                "本次数据量超过在线分析上限。完整数据请通过对应业务菜单导出，或联系系统管理员。",
                limitsNode, options("导出原始数据", "缩小查询范围", "只分析前 N 条"), interfaceCode);
    }

    /**
     * 单页响应被截断：数据不完整，不能用于分析或导出。
     */
    public static Map<String, Object> truncated(String interfaceCode, long rawBytes) {
        Map<String, Object> limitsNode = new LinkedHashMap<>();
        limitsNode.put("actualBytes", rawBytes);
        return body(AiErrors.DATA_TOO_LARGE,
                "接口单次返回的数据量过大，无法保证内容完整。请通过对应业务菜单导出，或缩小查询范围。",
                limitsNode, options("导出原始数据", "缩小查询范围"), interfaceCode);
    }

    /**
     * 单轮分析预算耗尽。
     */
    public static Map<String, Object> turnBudgetExceeded(String interfaceCode, AiTurnBudget budget) {
        Map<String, Object> limitsNode = new LinkedHashMap<>();
        limitsNode.put("maxChars", budget == null ? 0 : budget.getMaxChars());
        limitsNode.put("actualChars", budget == null ? 0 : budget.getUsedChars());
        return body(AiErrors.DATA_TOO_LARGE,
                "本轮分析数据量已达上限，继续读取会让回答不准确。请缩小范围，或通过对应业务菜单导出完整数据。",
                limitsNode, options("缩小查询范围", "导出原始数据"), interfaceCode);
    }

    /**
     * 无法保证全量导出的通用降级。
     */
    public static Map<String, Object> dataIncomplete(String message, Map<String, Object> detail) {
        return body(AiErrors.DATA_INCOMPLETE, message, detail, options("缩小查询范围"), null);
    }

    private static List<Map<String, Object>> options(String... labels) {
        return List.of(
                option("EXPORT_DATA", labels.length > 0 ? labels[0] : "导出原始数据"),
                option("NARROW_SCOPE", labels.length > 1 ? labels[1] : "缩小查询范围"),
                option("SAMPLE_ANALYSIS", labels.length > 2 ? labels[2] : "只分析前 N 条"));
    }

    private static Map<String, Object> option(String action, String label) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("action", action);
        option.put("label", label);
        return option;
    }

    private static Map<String, Object> body(String error, String message, Map<String, Object> limits,
                                            List<Map<String, Object>> options, String interfaceCode) {
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("type", TYPE_MENU_OR_ADMIN);
        handoff.put("message", message);
        handoff.put("suggestedAction", "MENU_EXPORT");
        handoff.put("options", options);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        body.put("truncated", true);
        if (interfaceCode != null) {
            body.put("interfaceCode", interfaceCode);
        }
        body.put("message", message);
        body.put("limits", limits);
        body.put("handoff", handoff);
        return body;
    }
}
