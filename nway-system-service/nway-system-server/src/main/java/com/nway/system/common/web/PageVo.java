package com.nway.system.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVo<T> {

    private List<T> data;

    private long total;

    public static final PageVo<?> EMPTY_PAGE = new PageVo<>(new ArrayList<>(0), 0);
}
