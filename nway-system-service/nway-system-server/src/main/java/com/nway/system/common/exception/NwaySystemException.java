package com.nway.system.common.exception;

import com.nway.system.common.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * 业务异常，携带错误码。
 */
@Getter
public class NwaySystemException extends RuntimeException {

    private final int code;

    public NwaySystemException(String message) {
        super(message);
        this.code = ErrorCodeEnum.BUSINESS_ERROR.getCode();
    }

    public NwaySystemException(int code, String message) {
        super(message);
        this.code = code;
    }

    public NwaySystemException(ErrorCodeEnum errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public NwaySystemException(ErrorCodeEnum errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public NwaySystemException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCodeEnum.BUSINESS_ERROR.getCode();
    }
}
