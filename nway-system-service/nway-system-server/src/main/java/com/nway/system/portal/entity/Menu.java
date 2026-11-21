package com.nway.system.portal.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 菜单（纯导航，权限由 sys_permission_policy 控制）。
 * <p>
 * 菜单不再承载权限语义（无 powerKey），其可见性由用户的权限策略推导：
 * <ul>
 *   <li>PAGE 类型：用户在对应 resourceCode 上有任意 action 权限 → 可见</li>
 *   <li>BTN 类型：用户在父页面的 resourceCode 上有对应 action 权限 → 可见</li>
 *   <li>DIR 类型：下级有可见菜单 → 可见</li>
 * </ul>
 */
@Data
@Table("sys_menu")
public class Menu extends BaseEntity {

    /** 来源：平台管理员配置（菜单归属「资源归属租户」语义，见 {@link #isResourceOwnerTenant()}) */
    public static final String SOURCE_PLATFORM = "PLATFORM";

    /** 来源：子应用自注册（同资源归属租户语义） */
    public static final String SOURCE_APP = "APP";

    /** 来源：低代码平台同步（菜单归属「页面租户」语义，见 {@link #isPageTenant()}) */
    public static final String SOURCE_LOWCODE = "LOWCODE";

    /** 菜单名称 */
    private String name;

    /** 上级菜单id，顶级为空串 */
    private String parentId;

    /** 页面路由路径（相对于 App.host） */
    private String url;

    /** 渲染类型：microApp/wujie/iFrame */
    private String renderType;

    /** 图标 */
    private String icon;

    /** 所属应用标识 */
    private String appCode;

    /** 资源标识（PAGE类型必填，与权限策略 resource_code 对应） */
    private String resourceCode;

    /** 权限资源id（sys_resource.id）：导航引用资源，权限语义以资源目录为准 */
    private String resourceId;

    /** 菜单类型：DIR(目录)/PAGE(页面)/BTN(按钮) */
    private String type;

    /** 操作标识（BTN类型必填，如 create/edit/delete/approve） */
    private String action;

    /** 排序 */
    private Integer sequence;

    /** 是否启用 */
    private Boolean isEnabled;

    /** 来源：PLATFORM(平台管理员配置)/APP(子应用自注册)/LOWCODE(低代码平台同步) */
    private String source;

    /** 外部唯一标识（低代码菜单同步幂等键，仅 source=LOWCODE 使用） */
    private String externalId;

    /** 是否在导航中显示（false=隐藏页面：可路由可授权但不进菜单） */
    private Boolean visible;

    /**
     * 菜单归属租户（{@code sys_menu.tenant_code}）的语义判定 —— 统一读取入口。
     * <p>
     * 该列承载两种语义，取决于 {@code source}：
     * <ul>
     *   <li>{@code source=LOWCODE}（低代码同步页面）：为「页面租户」——消费该页面的业务租户，
     *       页面表单数据也落在该租户，故<b>不能</b>随应用租户一起搬迁；</li>
     *   <li>其余来源（{@code PLATFORM}/{@code APP}）：为「资源归属租户」——平台内置资源归 default、
     *       共享应用资源归 public，对所有租户只读可见。</li>
     * </ul>
     * 任何按租户处理菜单的逻辑（可见性推导、租户级联、策略候选集）都应先经此判定，
     * 避免把两种语义混用。
     */
    public boolean isPageTenant() {
        return SOURCE_LOWCODE.equals(getSource());
    }

    /** 菜单归属「资源归属租户」语义（即非低代码页面），见 {@link #isPageTenant()} */
    public boolean isResourceOwnerTenant() {
        return !isPageTenant();
    }
}
