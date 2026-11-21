package com.nway.system.identity.web.model;

import com.nway.system.common.web.PageParam;
import lombok.Data;

import java.util.List;

/**
 * 用户信息
 */
@Data
public class UserQuery extends PageParam {
    /**
     * 工号
     */
    private String employeeId;
    /**
     * 真实姓名
     */
    private String realName;
    /**
     * 关键字：姓名或工号模糊搜索（管理员任命等场景专用）
     */
    private String keyword;
    /**
     * 关键字是否同时匹配手机号（默认 false，仅开放平台选人搜索使用；不影响既有查询行为）
     */
    private boolean keywordMatchPhone;
    /**
     * 用户状态筛选（using/disabled），为空时不筛选
     */
    private String status;
    /**
     * 数据权限：部门id集合（DEPT/DEPT_WITH_SUB/CUSTOM 注入），由 UserService 自动填充
     */
    private List<String> deptIdIn;
    /**
     * 数据权限：仅看本人创建（SELF 注入），由 UserService 自动填充
     */
    private String creatorId;
    /**
     * 按租户编码过滤（用于角色绑定用户时指定角色所属租户）
     */
    private String tenantCode;

}
