package com.nway.system.portal.domain;

import lombok.Data;

@Data
public class MenuBo {
    private String id;
    private String name;
    private String parentId;
    private String url;
    private String icon;
    private String appCode;
    private String resourceCode;
    private String action;
    private String type;
    private String renderType;
    private Integer sequence;
    private Boolean isEnabled;
}
