package com.nway.system.ai.web.model;

import lombok.Data;

import java.util.List;

/**
 * 会话导出请求：默认导出截至上一轮已落库的消息，不重新调用大模型分析。
 *
 * @author nway
 */
@Data
public class AiConversationExportRequest {

    /** docx / xlsx / html / pptx，默认 docx */
    private String format;

    /** ALL / LAST_N / SELECTED，默认 ALL */
    private String scope;

    /** scope=LAST_N 时导出最近 N 条消息 */
    private Integer lastN;

    /** scope=SELECTED 时导出指定消息 */
    private List<String> messageIds;

    /** 可选时间范围，格式 yyyy-MM-dd */
    private TimeRange timeRange;

    /** 是否包含工具结果表格，默认 true */
    private Boolean includeToolResults;

    /** 是否包含附件名称与链接，默认 true */
    private Boolean includeAttachments;

    @Data
    public static class TimeRange {
        private String start;
        private String end;
    }
}
