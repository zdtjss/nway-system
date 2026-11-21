package com.nway.system.portal.web.model;

import lombok.Data;

/**
 * 低代码平台菜单同步项。
 * <p>
 * 父级引用双轨制（互斥）：
 * <ul>
 *   <li>{@code parentMenuId}：引用本系统已有菜单（须为同应用下的 DIR），用于挂载到平台已有菜单树</li>
 *   <li>{@code externalParentId}：引用同批次某项的 externalId（该项须为 DIR），用于批次内自建子树；
 *       也可指向历史批次已发布的 LOWCODE 节点实现增量挂载</li>
 * </ul>
 * 两者都空：新建=挂应用根；更新=保持原父级不变。
 * <p>
 * {@code attachToAppRoot=true} 显式表达"挂到应用根"：新建与更新语义一致
 * （更新时会把已有菜单从原父级移到应用根），用于低代码侧"页面/目录直接归属应用"
 * 的场景（如根目录即应用、页面直接挂根）。与上两个父级字段互斥。
 */
@Data
public class LowcodeMenuSyncItem {

    /** 低代码侧唯一标识（幂等键，重复发布=更新） */
    private String externalId;

    /** 本系统已有父菜单id（与 externalParentId 互斥） */
    private String parentMenuId;

    /** 同批次/历史批次的父级低代码标识（与 parentMenuId 互斥，指向项须为 DIR） */
    private String externalParentId;

    /** 是否显式挂到应用根（与 parentMenuId/externalParentId 互斥；缺省=沿用旧语义） */
    private Boolean attachToAppRoot;

    /** 类型：DIR(目录)/PAGE(页面)。低代码"目录"→DIR、"页面"→PAGE，不支持 BTN */
    private String type;

    /** 菜单名称 */
    private String name;

    /** 页面路由路径（相对于目标应用 host，PAGE 必填；DIR 不可携带） */
    private String url;

    /** 资源标识（PAGE 必填，与权限策略关联，控制菜单可见性） */
    private String resourceCode;

    /** 渲染类型：microApp/wujie/iFrame，缺省 microApp */
    private String renderType;

    /** 图标（可选） */
    private String icon;

    /** 是否在导航中显示，缺省 true。表单详情/编辑形态传 false：可路由可授权但不进菜单 */
    private Boolean visible;

    /** 是否启用，缺省 true */
    private Boolean isEnabled;

    /** 组内排序（可选） */
    private Integer sequence;
}
