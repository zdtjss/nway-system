package com.nway.system.authorization.web.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 数据权限规则 DTO（增强版）
 */
@Data
public class DataPermissionDto {

    private String id;

    /**
     * 主体类型：ROLE/USER/POSITION（默认 ROLE）
     */
    private String subjectType;

    /**
     * 主体id（角色id/工号/岗位id）
     */
    private String subjectId;

    /**
     * 主体名称（仅用于列表展示，不入库；由 service 按主体类型批量填充）
     */
    private String subjectName;

    /**
     * 兼容旧接口：roleId 映射为 subjectType=ROLE, subjectId=roleId
     */
    private String roleId;

    @NotBlank(message = "业务代号不能为空")
    private String bizCode;

    @NotBlank(message = "资源标识不能为空")
    private String resourceCode;

    @NotBlank(message = "数据范围不能为空")
    private String scopeType;

    /**
     * CUSTOM 时存部门id列表
     */
    private List<String> deptIds;

    /**
     * 条件表达式 JSON（EXPRESSION 时使用）
     */
    private String conditionExpr;

    /**
     * 优先级（越大越优先）
     */
    private Integer priority;

    private String remark;

    /**
     * 租户编码（超管新增时可指定归属租户）
     */
    private String tenantCode;

    /**
     * 租户名称（仅用于列表展示，由 service 批量填充）
     */
    private String tenantName;

    /**
     * 获取有效的 subjectId（兼容旧 roleId 字段）
     */
    public String getEffectiveSubjectId() {
        if (subjectId != null && !subjectId.isBlank()) {
            return subjectId;
        }
        return roleId;
    }
}
