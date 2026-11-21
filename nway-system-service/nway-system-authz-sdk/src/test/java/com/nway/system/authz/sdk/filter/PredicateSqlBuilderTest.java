package com.nway.system.authz.sdk.filter;

import com.nway.system.authz.sdk.model.AuthzBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据谓词 → SQL：参数化、fail-closed、字段名白名单。
 */
class PredicateSqlBuilderTest {

    private static final PredicateSqlBuilder.FieldMapping MAPPING =
            new PredicateSqlBuilder.FieldMapping("created_by", "dept_code");

    @Test
    @DisplayName("ALL：不追加条件")
    void allMeansNoFilter() {
        assertThat(PredicateSqlBuilder.build(
                new AuthzBundle.DataPredicate("ALL", List.of()), MAPPING)).isNull();
    }

    @Test
    @DisplayName("SELF：创建人等于工号，走占位符")
    void selfPredicate() {
        PredicateSqlBuilder.SqlFragment fragment = PredicateSqlBuilder.build(
                new AuthzBundle.DataPredicate("SELF", List.of("E1")), MAPPING);
        assertThat(fragment.sql()).isEqualTo("created_by = ?");
        assertThat(fragment.params()).containsExactly("E1");
    }

    @Test
    @DisplayName("DEPT_LIST：IN 占位符 + 参数列表")
    void deptListPredicate() {
        PredicateSqlBuilder.SqlFragment fragment = PredicateSqlBuilder.build(
                new AuthzBundle.DataPredicate("DEPT_LIST", List.of("D01", "D02")), MAPPING);
        assertThat(fragment.sql()).isEqualTo("dept_code IN (?,?)");
        assertThat(fragment.params()).containsExactly("D01", "D02");
    }

    @Test
    @DisplayName("DEPT_TREE：前缀 LIKE，参数追加 %")
    void deptTreePredicate() {
        PredicateSqlBuilder.SqlFragment fragment = PredicateSqlBuilder.build(
                new AuthzBundle.DataPredicate("DEPT_TREE", List.of("D01")), MAPPING);
        assertThat(fragment.sql()).isEqualTo("dept_code LIKE ?");
        assertThat(fragment.params()).containsExactly("D01%");
    }

    @Test
    @DisplayName("NONE/未知：恒假条件（fail-closed）")
    void unknownScopeDenies() {
        assertThat(PredicateSqlBuilder.build(
                new AuthzBundle.DataPredicate("NONE", List.of()), MAPPING).sql()).isEqualTo("1 = 0");
        assertThat(PredicateSqlBuilder.build(
                new AuthzBundle.DataPredicate("EXPRESSION", List.of("x")), MAPPING).sql()).isEqualTo("1 = 0");
    }

    @Test
    @DisplayName("多谓词并集：ALL 优先；空列表拒绝")
    void buildAny() {
        assertThat(PredicateSqlBuilder.buildAny(List.of(
                new AuthzBundle.DataPredicate("SELF", List.of("E1")),
                new AuthzBundle.DataPredicate("ALL", List.of())), MAPPING)).isNull();

        PredicateSqlBuilder.SqlFragment fragment = PredicateSqlBuilder.buildAny(List.of(
                new AuthzBundle.DataPredicate("SELF", List.of("E1")),
                new AuthzBundle.DataPredicate("DEPT_LIST", List.of("D01"))), MAPPING);
        assertThat(fragment.sql()).isEqualTo("(created_by = ? OR dept_code IN (?))");
        assertThat(fragment.params()).containsExactly("E1", "D01");

        assertThat(PredicateSqlBuilder.buildAny(List.of(), MAPPING).sql()).isEqualTo("1 = 0");
    }

    @Test
    @DisplayName("非法字段名：拒绝拼接（防注入）")
    void invalidIdentifierRejected() {
        assertThatThrownBy(() -> PredicateSqlBuilder.build(
                new AuthzBundle.DataPredicate("SELF", List.of("E1")),
                new PredicateSqlBuilder.FieldMapping("created_by; drop table x", "dept_code")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
