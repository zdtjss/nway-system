package com.nway.system.ai.service;

/**
 * 面向模型与调用方的错误码（与《AI 接口元数据规范 v2.0》第 18 章一致）。
 *
 * @author nway
 */
public final class AiErrors {

    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String NEED_CLARIFICATION = "NEED_CLARIFICATION";
    public static final String INVALID_OPTION = "INVALID_OPTION";
    public static final String OPTION_SOURCE_UNAVAILABLE = "OPTION_SOURCE_UNAVAILABLE";
    public static final String USER_CONTEXT_MISSING = "USER_CONTEXT_MISSING";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String CONFIRM_REQUIRED = "CONFIRM_REQUIRED";
    public static final String TARGET_TIMEOUT = "TARGET_TIMEOUT";
    public static final String TARGET_BUSINESS_ERROR = "TARGET_BUSINESS_ERROR";
    public static final String TARGET_ERROR = "TARGET_ERROR";
    public static final String RESPONSE_PARSE_ERROR = "RESPONSE_PARSE_ERROR";
    public static final String MODEL_ERROR = "MODEL_ERROR";
    public static final String SCHEMA_ERROR = "SCHEMA_ERROR";
    public static final String NO_MATCH = "NO_MATCH";
    /** 响应体超过单次读取上限，内容不完整 */
    public static final String DATA_TOO_LARGE = "DATA_TOO_LARGE";
    /** 查询/导出过程中无法保证数据完整（无分页、分页截断、total 对账不一致等） */
    public static final String DATA_INCOMPLETE = "DATA_INCOMPLETE";
    /** 导出规模超过上限 */
    public static final String EXPORT_LIMIT_EXCEEDED = "EXPORT_LIMIT_EXCEEDED";

    private AiErrors() {
    }
}
