package com.nway.system.authorization.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import com.nway.spring.jdbc.sql.LogicFieldStrategy;
import com.nway.spring.jdbc.sql.fill.StringIdStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限资源（与导航菜单解耦）。
 * <p>
 * 不继承 BaseEntity：资源目录是跨租户的元数据（PLATFORM/SHARED 对所有租户可见），
 * 租户隔离由 {@link #tenantScope} 显式表达，而不是行级 tenant_code 过滤。
 */
@Data
@Table("sys_resource")
public class SysResource {

    /** 全平台可用（平台内置应用的资源） */
    public static final String SCOPE_PLATFORM = "PLATFORM";
    /** 所有租户可授权（共享应用资源） */
    public static final String SCOPE_SHARED = "SHARED";
    /** 租户私有 */
    public static final String SCOPE_TENANT = "TENANT";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DEPRECATED = "DEPRECATED";

    public static final String TYPE_PAGE = "PAGE";
    public static final String TYPE_API = "API";
    public static final String TYPE_CAPABILITY = "CAPABILITY";
    public static final String TYPE_DATA = "DATA";
    public static final String TYPE_FIELD = "FIELD";

    @Column(type = ColumnType.ID, fillStrategy = StringIdStrategy.class)
    private String id;

    /** 所属应用 */
    private String appCode;

    /** 资源标识（应用内唯一） */
    private String resourceCode;

    /** 资源类型：PAGE/API/CAPABILITY/DATA/FIELD */
    private String resourceType;

    /** 资源名称 */
    private String name;

    /** 说明 */
    private String description;

    /** 上级资源id（能力分组） */
    private String parentId;

    /** PLATFORM/SHARED/TENANT */
    private String tenantScope;

    /** 资源归属租户（策略写入作用的租户） */
    private String ownerTenantCode;

    /** ACTIVE/DEPRECATED */
    private String status;

    /** PLATFORM/APP/LOWCODE */
    private String source;

    /** 最近一次 manifest 校验哈希 */
    private String manifestHash;

    private Integer sort;

    private String creatorId;

    private String creatorName;

    private String modifierId;

    private String modifierName;

    private LocalDateTime createTime;

    private LocalDateTime modifiedTime;

    @Column(fillStrategy = LogicFieldStrategy.class, permissionStrategy = LogicFieldStrategy.class)
    private Boolean isDeleted;

    /** 是否所有租户都可授权 */
    public boolean isShareable() {
        return SCOPE_PLATFORM.equals(tenantScope) || SCOPE_SHARED.equals(tenantScope);
    }
}
