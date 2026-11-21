package com.nway.system.common.web;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.nway.system.common.web.Result;
import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.common.exception.NwaySystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@ResponseBody
@ControllerAdvice
public class GlobalExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public Result<String> runtimeExceptionHandler(Exception ex) {
        logger.error(ex.getMessage(), ex);
        return Result.error(ErrorCodeEnum.INTERNAL_ERROR);
    }

    @ExceptionHandler(NwaySystemException.class)
    public Result<String> businessExceptionHandler(NwaySystemException ex) {
        logger.warn("业务异常: code={}, msg={}", ex.getCode(), ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        logger.warn("参数校验失败: {}", errorMessage);
        return Result.error(ErrorCodeEnum.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<String> accessDeniedExceptionHandler(AccessDeniedException ex) {
        logger.warn("无权限访问: {}", ex.getMessage());
        return Result.error(ErrorCodeEnum.FORBIDDEN);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<String> noHandlerFoundExceptionHandler(NoHandlerFoundException ex) {
        logger.warn("未找到处理器: {}", ex.getRequestURL());
        return Result.error(ErrorCodeEnum.FORBIDDEN);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<String> methodNotSupportedExceptionHandler(HttpRequestMethodNotSupportedException ex) {
        logger.warn("不支持的请求方法: {}", ex.getMessage());
        return Result.error(ErrorCodeEnum.METHOD_NOT_SUPPORTED);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<String> maxUploadSizeExceededHandler(MaxUploadSizeExceededException ex) {
        logger.warn("上传文件过大: {}", ex.getMessage());
        return Result.error(ErrorCodeEnum.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> httpMessageNotReadableHandler(HttpMessageNotReadableException ex) {
        logger.warn("请求参数格式错误: {}", ex.getMessage());
        String detail = describeTypeMismatch(ex);
        return detail == null
                ? Result.error(ErrorCodeEnum.BAD_REQUEST)
                : Result.error(ErrorCodeEnum.BAD_REQUEST, ErrorCodeEnum.BAD_REQUEST.getMsg() + "：" + detail);
    }

    /**
     * 从 Jackson 反序列化异常中提取出错字段与期望类型，避免客户端只能看到笼统的「请求参数格式错误」。
     */
    private String describeTypeMismatch(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof MismatchedInputException mismatch)
                || mismatch.getPath().isEmpty() || mismatch.getTargetType() == null) {
            return null;
        }
        String field = mismatch.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : String.valueOf(ref.getIndex()))
                .collect(Collectors.joining("."));
        return "字段 " + field + " 应为 " + jsonTypeName(mismatch.getTargetType());
    }

    private String jsonTypeName(Class<?> type) {
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "JSON 数组";
        }
        if (Map.class.isAssignableFrom(type)) {
            return "JSON 对象";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "布尔值";
        }
        if (type == String.class || type == Character.class) {
            return "字符串";
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return "数字";
        }
        return "对象";
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result<String> missingRequestHeaderExceptionHandler(MissingRequestHeaderException ex) {
        logger.warn("缺少请求头参数: {}", ex.getMessage());
        return Result.error(ErrorCodeEnum.BAD_REQUEST);
    }
}
