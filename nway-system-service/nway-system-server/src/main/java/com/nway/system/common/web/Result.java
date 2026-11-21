package com.nway.system.common.web;

import com.nway.system.common.enums.ErrorCodeEnum;
import lombok.Data;

@Data
public class Result<T> {

    private int code = 200;
    private String msg;
    private T data;

    public static <T> Result<T> ok() {
        return new Result<>();
    }

    public static <T> Result<T> ok(T object) {
        Result<T> r = new Result<>();
        r.setData(object);
        return r;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> r = new Result<>();
        r.setCode(500);
        r.setMsg(msg);
        return r;
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public static <T> Result<T> error(ErrorCodeEnum errorCode) {
        Result<T> r = new Result<>();
        r.setCode(errorCode.getCode());
        r.setMsg(errorCode.getMsg());
        return r;
    }

    public static <T> Result<T> error(ErrorCodeEnum errorCode, String msg) {
        Result<T> r = new Result<>();
        r.setCode(errorCode.getCode());
        r.setMsg(msg);
        return r;
    }
}
