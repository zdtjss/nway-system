package com.nway.system.authorization.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合并策略动作判定测试（D3：全放行语义显式化）。
 */
@DisplayName("MergedPolicy：全部动作放行语义")
class MergedPolicyTest {

    @Test
    @DisplayName("actions=null：历史语义，全部动作放行")
    void nullActionsMeansAll() {
        MergedPolicy p = new MergedPolicy();
        p.setActions(null);

        assertThat(p.hasAction("view")).isTrue();
        assertThat(p.hasAction("delete")).isTrue();
        assertThat(p.hasAnyAction()).isTrue();
    }

    @Test
    @DisplayName("actions 含 '*'：显式语义，全部动作放行")
    void wildcardActionsMeansAll() {
        MergedPolicy p = new MergedPolicy();
        p.setActions(Set.of(MergedPolicy.ALL_ACTIONS));

        assertThat(p.hasAction("view")).isTrue();
        assertThat(p.hasAction("reset")).isTrue();
        assertThat(p.hasAnyAction()).isTrue();
    }

    @Test
    @DisplayName("显式动作集合：仅命中集合内动作")
    void explicitActionsAreLimited() {
        MergedPolicy p = new MergedPolicy();
        p.setActions(Set.of("view", "edit"));

        assertThat(p.hasAction("view")).isTrue();
        assertThat(p.hasAction("edit")).isTrue();
        assertThat(p.hasAction("delete")).isFalse();
        assertThat(p.hasAnyAction()).isTrue();
    }

    @Test
    @DisplayName("空动作集合：无任何操作权限")
    void emptyActionsDeniesAll() {
        MergedPolicy p = new MergedPolicy();
        p.setActions(Set.of());

        assertThat(p.hasAction("view")).isFalse();
        assertThat(p.hasAnyAction()).isFalse();
    }
}
