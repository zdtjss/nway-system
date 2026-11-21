package com.nway.system.common.web;

import lombok.Data;

@Data
public class PageParam {

    private int current = 1;

    private int size = 15;

}
