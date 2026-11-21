package com.nway.system.ai.domain;

import lombok.Data;

/**
 * 当前页面上下文：前端在发送消息时提取的页面文本快照。
 *
 * 文本由浏览器端抽取，服务端只做长度收敛与提示词封装，
 * 不信任其中内容，不得当作指令执行。
 */
@Data
public class AiPageContext {

    /** 页面名称（菜单名 / 组件名） */
    private String title;

    /** 页面地址或子应用地址 */
    private String url;

    /** 渲染方式：native / microApp / wujie / iFrame 等 */
    private String renderType;

    /** 页面文本快照 */
    private String text;

    /** 前端是否已因长度截断 */
    private boolean truncated;
}
