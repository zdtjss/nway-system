package com.nway.system.authz.sdk.filter;

import com.nway.system.authz.sdk.model.ResourcePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 权限 SQL WHERE 构建器。
 * <p>
 * 根据 {@link ResourcePolicy} 的 dataScope 构建 SQL WHERE 条件片段。
 * <p>
 * 用法：
 * <pre>{@code
 * ResourcePolicy policy = client.getPolicy(employeeId, "contract");
 * SqlFragment fragment = PermissionFilter.buildDataScopeWhere(policy, FieldMapping.DEFAULT, userVars);
 * if (fragment != null) {
 *     sql += " AND " + fragment.sql();
 *     params.addAll(fragment.params());
 * }
 * }</pre>
 */
public final class PermissionFilter {

    private PermissionFilter() {
    }

    /**
     * SQL 片段：sql + 参数列表
     */
    public record SqlFragment(String sql, List<Object> params) {
    }

    /**
     * 字段映射。
     */
    public record FieldMapping(String deptIdField, String deptCodeField, String creatorField) {
        public static final FieldMapping DEFAULT = new FieldMapping("dept_id", "dept_code", "creator_id");
    }

    /**
     * 根据策略的 dataScope 构建 WHERE 条件。
     *
     * @param policy       资源策略（从平台获取）
     * @param fieldMapping 字段映射
     * @param variables    用户变量（至少含 user.employeeId）
     * @return SQL 片段；ALL 范围或无策略返回 null（不追加条件）
     */
    public static SqlFragment buildDataScopeWhere(ResourcePolicy policy,
                                                  FieldMapping fieldMapping,
                                                  Map<String, Object> variables) {
        if (policy == null) {
            // 无策略 = 拒绝所有数据
            return new SqlFragment("1 = 0", List.of());
        }

        String scope = policy.dataScope();
        // 缺失/空范围一律拒绝：平台少返回一个字段就静默放开，是比报错更危险的失败方式
        if (scope == null || scope.isBlank()) {
            return new SqlFragment("1 = 0", List.of());
        }
        if ("ALL".equalsIgnoreCase(scope)) {
            return null; // 不限制
        }

        return switch (scope.toUpperCase()) {
            case "SELF" -> {
                Object employeeId = variables.get("user.employeeId");
                yield employeeId != null
                        ? new SqlFragment(fieldMapping.creatorField() + " = ?", List.of(employeeId))
                        : new SqlFragment("1 = 0", List.of());
            }
            case "DEPT", "CUSTOM" -> buildDeptIdClause(policy, fieldMapping);
            case "DEPT_WITH_SUB" -> buildDeptCodeClause(policy, fieldMapping);
            default -> new SqlFragment("1 = 0", List.of());
        };
    }

    private static SqlFragment buildDeptIdClause(ResourcePolicy policy, FieldMapping fieldMapping) {
        List<String> deptIds = policy.deptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            return new SqlFragment("1 = 0", List.of());
        }
        String placeholders = String.join(",", deptIds.stream().map(d -> "?").toList());
        return new SqlFragment(
                fieldMapping.deptIdField() + " IN (" + placeholders + ")",
                new ArrayList<>(deptIds)
        );
    }

    private static SqlFragment buildDeptCodeClause(ResourcePolicy policy, FieldMapping fieldMapping) {
        List<String> deptCodes = policy.deptCodes();
        if (deptCodes == null || deptCodes.isEmpty()) {
            return new SqlFragment("1 = 0", List.of());
        }
        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < deptCodes.size(); i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append(fieldMapping.deptCodeField()).append(" LIKE ? ESCAPE '\\'");
            String code = deptCodes.get(i)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            params.add(code + "%");
        }
        return new SqlFragment(sb.toString(), params);
    }
}
