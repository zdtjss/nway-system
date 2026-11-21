package com.nway.system.portal.domain;

import lombok.Data;

import java.util.List;

/**
 * 菜单树节点 BO
 */
@Data
public class MenuTreeBo extends MenuBo {

    private List<MenuTreeBo> children;
}
