package com.nway.system.portal.web.model;

import com.nway.system.portal.entity.Menu;
import lombok.Data;

/**
 * 低代码平台菜单树节点（选择父级菜单用，仅含 DIR/PAGE）。
 */
@Data
public class LowcodeMenuNodeVo {

    /** 本系统菜单id（作为 parentMenuId 引用） */
    private String menuId;

    /** 父菜单id（顶级为空串） */
    private String parentId;

    /** 菜单名称 */
    private String name;

    /** 类型：DIR/PAGE */
    private String type;

    /** 页面路由路径 */
    private String url;

    /** 外部唯一标识（LOWCODE 来源菜单有值，供低代码平台对照） */
    private String externalId;

    /** 是否在导航中显示 */
    private Boolean visible;

    public static LowcodeMenuNodeVo from(Menu menu) {
        LowcodeMenuNodeVo vo = new LowcodeMenuNodeVo();
        vo.setMenuId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setName(menu.getName());
        vo.setType(menu.getType());
        vo.setUrl(menu.getUrl());
        vo.setExternalId(menu.getExternalId());
        vo.setVisible(menu.getVisible());
        return vo;
    }
}
