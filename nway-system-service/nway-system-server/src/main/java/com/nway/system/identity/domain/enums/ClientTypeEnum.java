package com.nway.system.identity.domain.enums;

import com.nway.system.common.exception.NwaySystemException;

import java.util.Arrays;
import java.util.Optional;

/**
 * 登录端类型
 */
public enum ClientTypeEnum {

    /**
     * 移动端
     */
    MOBILE("mobile"),
    /**
     * PC端
     */
    PC("pc");

    private String value;

    ClientTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static ClientTypeEnum of(String name) {
        Optional<ClientTypeEnum> first = Arrays.stream(values()).filter(e -> e.getValue().equals(name)).findFirst();
        if (first.isPresent()) {
            return first.get();
        }
        throw new NwaySystemException("无效客户端类型");
    }
}
