package com.nway.system.authz.sdk.filter;

import com.nway.system.authz.sdk.model.AuthzBundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据谓词 → 参数化 SQL 片段。
 * <p>
 * 安全约束：
 * <ul>
 *   <li>字段名白名单校验（仅 {@code [A-Za-z_][A-Za-z0-9_]*}），拒绝拼接任意 SQL；</li>
 *   <li>值全部走占位符参数；</li>
 *   <li>未知谓词/NONE → {@code 1=0}（fail-closed）；空谓词列表同样拒绝。</li>
 * </ul>
 */
public final class PredicateSqlBuilder {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String DENY = "1 = 0";

    private PredicateSqlBuilder() {
    }

    public record FieldMapping(String creatorColumn, String deptCodeColumn) {
        public static final FieldMapping DEFAULT = new FieldMapping("created_by", "dept_code");
    }

    public record SqlFragment(String sql, List<Object> params) {
    }

    /**
     * 单个谓词。返回 null 表示不限制（仅 ALL）。
     */
    public static SqlFragment build(AuthzBundle.DataPredicate predicate, FieldMapping mapping) {
        if (predicate == null || predicate.kind() == null) {
            return new SqlFragment(DENY, List.of());
        }
        FieldMapping fields = mapping == null ? FieldMapping.DEFAULT : mapping;
        return switch (predicate.kind()) {
            case AuthzBundle.DataPredicate.ALL -> null;
            case AuthzBundle.DataPredicate.SELF -> {
                String column = identifier(fields.creatorColumn());
                List<String> values = safeValues(predicate);
                yield values.isEmpty()
                        ? new SqlFragment(DENY, List.of())
                        : new SqlFragment(column + " = ?", List.of(values.get(0)));
            }
            case AuthzBundle.DataPredicate.DEPT, AuthzBundle.DataPredicate.DEPT_LIST -> {
                String column = identifier(fields.deptCodeColumn());
                List<String> values = safeValues(predicate);
                yield values.isEmpty()
                        ? new SqlFragment(DENY, List.of())
                        : new SqlFragment(column + " IN (" + placeholders(values.size()) + ")",
                                new ArrayList<Object>(values));
            }
            case AuthzBundle.DataPredicate.DEPT_TREE -> {
                String column = identifier(fields.deptCodeColumn());
                List<String> values = safeValues(predicate);
                if (values.isEmpty()) {
                    yield new SqlFragment(DENY, List.of());
                }
                StringBuilder sql = new StringBuilder();
                List<Object> params = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        sql.append(" OR ");
                    }
                    sql.append(column).append(" LIKE ?");
                    params.add(values.get(i) + "%");
                }
                yield new SqlFragment(sql.toString(), params);
            }
            default -> new SqlFragment(DENY, List.of());
        };
    }

    /**
     * 多个谓词取并集（OR）。任一条 ALL → 不限制；空列表 → 拒绝。
     */
    public static SqlFragment buildAny(List<AuthzBundle.DataPredicate> predicates, FieldMapping mapping) {
        if (predicates == null || predicates.isEmpty()) {
            return new SqlFragment(DENY, List.of());
        }
        List<SqlFragment> fragments = new ArrayList<>();
        for (AuthzBundle.DataPredicate predicate : predicates) {
            SqlFragment fragment = build(predicate, mapping);
            if (fragment == null) {
                return null; // ALL
            }
            fragments.add(fragment);
        }
        if (fragments.isEmpty()) {
            return new SqlFragment(DENY, List.of());
        }
        StringBuilder sql = new StringBuilder("(");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append(fragments.get(i).sql());
            params.addAll(fragments.get(i).params());
        }
        sql.append(")");
        return new SqlFragment(sql.toString(), params);
    }

    private static List<String> safeValues(AuthzBundle.DataPredicate predicate) {
        return predicate.values() == null ? List.of() : predicate.values();
    }

    private static String identifier(String column) {
        if (column == null || !IDENTIFIER.matcher(column).matches()) {
            throw new IllegalArgumentException("非法字段名：" + column);
        }
        return column;
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    /**
     * 转换为 MyBatis 命名参数形式：{@code ?} → {@code #{prefixN}}，并返回参数映射。
     * <p>
     * 返回 null 表示不限制（ALL）；谓词为空时返回恒假条件。
     */
    public static MyBatisWhere toMybatis(List<AuthzBundle.DataPredicate> predicates,
                                         FieldMapping mapping, String prefix) {
        SqlFragment fragment = buildAny(predicates, mapping);
        if (fragment == null) {
            return null;
        }
        String safePrefix = (prefix == null || prefix.isBlank()) ? "authz" : prefix;
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        StringBuilder sql = new StringBuilder();
        int index = 0;
        for (int i = 0; i < fragment.sql().length(); i++) {
            char c = fragment.sql().charAt(i);
            if (c == '?') {
                String name = safePrefix + index;
                sql.append("#{").append(name).append("}");
                params.put(name, fragment.params().get(index));
                index++;
            } else {
                sql.append(c);
            }
        }
        return new MyBatisWhere(sql.toString(), params);
    }

    public record MyBatisWhere(String sql, Map<String, Object> params) {
    }
}
