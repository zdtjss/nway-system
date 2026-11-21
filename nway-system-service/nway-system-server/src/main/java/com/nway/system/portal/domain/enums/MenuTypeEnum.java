package com.nway.system.portal.domain.enums;

import com.nway.system.common.exception.NwaySystemException;

import java.util.Arrays;
import java.util.Optional;

public enum MenuTypeEnum {
    /**
     * 移动端
     */
    DIR("dir"),
    /**
     * PC端
     */
    PAGE("page"),
    /**
     * 按钮
     */
    BTN("btn");

    private String code;

    MenuTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }

    public static MenuTypeEnum of(String name) {
        Optional<MenuTypeEnum> first = Arrays.stream(values()).filter(e -> e.getCode().equals(name)).findFirst();
        if (first.isPresent()) {
            return first.get();
        }
        throw new NwaySystemException("无效的菜单类型");
    }
}
