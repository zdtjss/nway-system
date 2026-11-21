package com.nway.system.identity.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 角色。
 * <p>
 * 角色是权限策略的主要载体。通过 sys_permission_policy 关联资源策略。
 * <ul>
 *   <li>appCode 限定角色的权限范围在某个应用内</li>
 *   <li>appCode = '*' 表示平台级角色，可跨应用授权</li>
 * </ul>
 * 唯一约束：(role_code, app_code, tenant_code)
 */
@Data
@Table("sys_role")
public class Role extends BaseEntity {

    /** 角色名称 */
    private String roleName;

    /** 角色编码 */
    private String roleCode;

    /**
     * 所属应用标识。
     * <p>
     * 限定角色的权限范围在某个应用内，'*' 表示平台级跨应用角色。
     */
    private String appCode;

    /** 角色描述 */
    private String description;
}
