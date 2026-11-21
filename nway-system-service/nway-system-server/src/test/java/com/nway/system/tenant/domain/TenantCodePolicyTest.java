package com.nway.system.tenant.domain;

import com.nway.system.common.exception.NwaySystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户编码规则：与 contracts/host-tenant.json 一致（2-20，字母开头，大小写敏感）。
 */
class TenantCodePolicyTest {

    @Test
    @DisplayName("合法编码：小写、大写、下划线、长度 20")
    void validCodes() {
        assertThat(TenantCodePolicy.isValid("oa")).isTrue();
        assertThat(TenantCodePolicy.isValid("OA")).isTrue();
        assertThat(TenantCodePolicy.isValid("acme_01")).isTrue();
        assertThat(TenantCodePolicy.isValid("a".repeat(20))).isTrue();
    }

    @Test
    @DisplayName("非法编码：数字开头、超长、含中划线/中文、空")
    void invalidCodes() {
        assertThat(TenantCodePolicy.isValid("1oa")).isFalse();
        assertThat(TenantCodePolicy.isValid("a".repeat(21))).isFalse();
        assertThat(TenantCodePolicy.isValid("oa-1")).isFalse();
        assertThat(TenantCodePolicy.isValid("租户")).isFalse();
        assertThat(TenantCodePolicy.isValid("")).isFalse();
        assertThat(TenantCodePolicy.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("requireValid：非法编码抛出业务异常（fail-closed）")
    void requireValidFailsClosed() {
        assertThat(TenantCodePolicy.requireValid("acme")).isEqualTo("acme");
        assertThatThrownBy(() -> TenantCodePolicy.requireValid("a".repeat(21)))
                .isInstanceOf(NwaySystemException.class)
                .hasMessageContaining("租户编码格式非法");
    }
}
