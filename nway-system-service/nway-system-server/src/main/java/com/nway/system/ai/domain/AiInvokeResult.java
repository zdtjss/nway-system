package com.nway.system.ai.domain;

import lombok.Data;

/**
 * 目标接口调用结果。
 *
 * @author nway
 */
@Data
public class AiInvokeResult {

    private boolean success;

    private String interfaceCode;

    private Object code;

    private String message;

    private Object data;

    private long latencyMs;

    /** 失败时的错误码（见 AiErrors） */
    private String errorCode;

    /** 目标响应原始字节数（用于判断是否被上限截断） */
    private long rawBytes;

    /** 响应体超过单次读取上限，data 不可用于分析或导出 */
    private boolean truncated;

    public static AiInvokeResult failed(String interfaceCode, String errorCode, String message) {
        AiInvokeResult result = new AiInvokeResult();
        result.setSuccess(false);
        result.setInterfaceCode(interfaceCode);
        result.setErrorCode(errorCode);
        result.setMessage(message);
        return result;
    }
}
