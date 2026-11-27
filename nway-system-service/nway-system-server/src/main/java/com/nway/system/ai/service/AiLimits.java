package com.nway.system.ai.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 查询与导出链路的体量阈值。
 * <p>
 * 阈值只在服务端工具层使用，不交给模型估算；模型只能看到判定结果与 handoff 提示。
 *
 * @author nway
 */
@Getter
@Component
public class AiLimits {

    /** 前端展示上限：行数 */
    @Value("${nway.ai.limits.display-max-rows:200}")
    private int displayMaxRows = 200;

    /** 前端展示上限：字符数 */
    @Value("${nway.ai.limits.display-max-chars:200000}")
    private int displayMaxChars = 200_000;

    /** 进入模型分析的上限：字符数 */
    @Value("${nway.ai.limits.analysis-max-chars:8000}")
    private int analysisMaxChars = 8_000;

    /** 进入模型分析的上限：行数 */
    @Value("${nway.ai.limits.analysis-max-rows:100}")
    private int analysisMaxRows = 100;

    /** 进入模型分析的上限：接口 total */
    @Value("${nway.ai.limits.analysis-max-total:200}")
    private int analysisMaxTotal = 200;

    /** 单轮累计分析预算：所有进入模型上下文的工具结果字符总量 */
    @Value("${nway.ai.limits.turn-analysis-max-chars:24000}")
    private int turnAnalysisMaxChars = 24_000;

    /** 导出分页默认每页行数 */
    @Value("${nway.ai.export.data-page-size:200}")
    private int exportPageSize = 200;

    /** 单次导出最大行数 */
    @Value("${nway.ai.export.data-max-rows:200000}")
    private int exportMaxRows = 200_000;

    /** 单次导出最大页数 */
    @Value("${nway.ai.export.data-max-pages:1000}")
    private int exportMaxPages = 1000;

    /** 单次导出文件最大字节数 */
    @Value("${nway.ai.export.data-max-bytes:209715200}")
    private long exportMaxBytes = 209_715_200L;

    /** docx 导出最大行数：超过时提示改用 xlsx */
    @Value("${nway.ai.export.docx-max-rows:2000}")
    private int docxMaxRows = 2_000;

    /** 会话导出最大消息数 */
    @Value("${nway.ai.export.conversation-max-messages:2000}")
    private int conversationMaxMessages = 2_000;

    /** 会话导出最大字符数 */
    @Value("${nway.ai.export.conversation-max-chars:500000}")
    private int conversationMaxChars = 500_000;

    /** 批量只读查询开关：关闭后回退单次 query_interface */
    @Value("${nway.ai.tools.query-batch-enabled:true}")
    private boolean queryBatchEnabled = true;

    /** 单次批量查询最多接口数 */
    @Value("${nway.ai.tools.query-batch-max-calls:5}")
    private int queryBatchMaxCalls = 5;

    /** 会话导出开关 */
    @Value("${nway.ai.tools.conversation-export-enabled:true}")
    private boolean conversationExportEnabled = true;
}
