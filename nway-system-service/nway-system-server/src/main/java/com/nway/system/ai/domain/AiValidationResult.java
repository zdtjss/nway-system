package com.nway.system.ai.domain;

import lombok.Data;

import java.util.Map;

/**
 * 参数校验结果。
 *
 * @author nway
 */
@Data
public class AiValidationResult {

    private boolean valid = true;

    private String errorCode;

    private String message;

    private Map<String, Object> values;

    public static AiValidationResult ok(Map<String, Object> values) {
        AiValidationResult result = new AiValidationResult();
        result.setValues(values);
        return result;
    }

    public static AiValidationResult fail(String errorCode, String message) {
        AiValidationResult result = new AiValidationResult();
        result.setValid(false);
        result.setErrorCode(errorCode);
        result.setMessage(message);
        return result;
    }
}
