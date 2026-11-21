package com.nway.system.authorization.entity;

import com.nway.spring.jdbc.annotation.Column;
import com.nway.spring.jdbc.annotation.Table;
import com.nway.spring.jdbc.annotation.enums.ColumnType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授权版本（GLOBAL 或租户维度）。
 * <p>
 * 每次授权变更单调递增；缓存以版本为准失效，避免“逐人枚举用户”的 O(N) 失效。
 */
@Data
@Table("sys_authz_version")
public class SysAuthzVersion {

    public static final String GLOBAL_SCOPE = "GLOBAL";
    public static final String GLOBAL_ID = "ver_global";

    @Column(type = ColumnType.ID)
    private String id;

    /** GLOBAL 或租户编码 */
    private String scope;

    private Long version;

    private LocalDateTime updatedTime;
}
