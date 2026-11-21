package com.nway.system.common.enums;

import lombok.Getter;

/**
 * 系统统一错误码枚举。
 * <p>
 * 编码规则：
 * - 2xx：成功
 * - 4xx：客户端错误（HTTP 语义对齐）
 * - 5xx：服务端错误
 * - 1xxx：业务错误（细分场景）
 */
@Getter
public enum ErrorCodeEnum {

    SUCCESS(200, "操作成功"),

    BAD_REQUEST(400, "请求参数格式错误"),
    UNAUTHORIZED(401, "登录已失效，请重新登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_SUPPORTED(405, "不支持的请求方法"),
    CONFLICT(409, "资源已存在"),
    PAYLOAD_TOO_LARGE(413, "上传文件过大"),

    INTERNAL_ERROR(500, "系统错误"),

    BUSINESS_ERROR(1001, "业务处理失败"),
    RESOURCE_NOT_FOUND(1002, "资源不存在"),
    RESOURCE_ALREADY_EXISTS(1003, "资源已存在"),
    OPERATION_NOT_ALLOWED(1004, "操作不被允许"),

    PASSWORD_POLICY_VIOLATION(1005, "密码不符合策略要求"),
    ACCOUNT_LOCKED(1006, "账号已被锁定，请联系管理员"),
    CAPTCHA_ERROR(1007, "验证码错误"),
    LOGIN_FAIL_LIMIT(1008, "登录失败次数过多，请稍后再试"),
    DATA_PERMISSION_DENIED(1009, "数据权限不足"),
    ;

    private final int code;
    private final String msg;

    ErrorCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
