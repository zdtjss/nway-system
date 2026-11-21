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

    private AiErrors() {
    }
}
