package com.nway.system.authorization.web.model;

import lombok.Data;

import java.util.List;

/**
 * 数据权限规则 VO（对外 API 返回，子应用消费）。
 * <p>
 * 子应用根据 {@link #scopeType} 自行拼接 SQL 过滤条件：
 * <ul>
 *   <li>ALL：不追加条件</li>
 *   <li>SELF：WHERE creator_id = 当前用户工号</li>
 *   <li>DEPT：WHERE dept_id IN ({@link #deptIds})（多部门任职时为多个部门）</li>
 *   <li>DEPT_WITH_SUB：WHERE dept_code LIKE '部门code%'（使用 {@link #deptCodes} 前缀匹配）</li>
 *   <li>CUSTOM：WHERE dept_id IN ({@link #deptIds})</li>
 *   <li>EXPRESSION：使用 {@link #conditionExpr} 解析为 SQL 条件</li>
 * </ul>
 */
@Data
public class DataPermissionRule {

    private String bizCode;
    private String resourceCode;
    /**
     * 数据范围：ALL/SELF/DEPT/DEPT_WITH_SUB/CUSTOM/EXPRESSION
     */
    private String scopeType;
    /**
     * 部门id列表
     */
    private List<String> deptIds;
    /**
     * 部门编码列表（DEPT_WITH_SUB 时用于前缀匹配）
     */
    private List<String> deptCodes;
    /**
     * 条件表达式 JSON（EXPRESSION 时返回，子应用解析执行）
     */
    private String conditionExpr;
}
