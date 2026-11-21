package com.nway.system.identity.domain.enums;

import com.nway.system.common.exception.NwaySystemException;

import java.util.Arrays;
import java.util.Optional;

/**
 * 用户状态
 */
public enum UserStatusEnum {

    /**
     * 使用中
     */
    USING("using"),
    /**
     * 已禁用
     */
    DISABLED("disabled");

    private String value;

    UserStatusEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static UserStatusEnum of(String name) {
        Optional<UserStatusEnum> first = Arrays.stream(values()).filter(e -> e.getValue().equals(name)).findFirst();
        if (first.isPresent()) {
            return first.get();
        }
        throw new NwaySystemException("无效的用户状态");
    }
}
