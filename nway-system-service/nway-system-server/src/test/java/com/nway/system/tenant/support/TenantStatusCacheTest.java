package com.nway.system.tenant.support;

import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 租户可用性判定（P2-1 生命周期）：存在 + using + 未到期 才算可用；缺失/禁用/过期一律不可用。
 */
@DisplayName("TenantStatusCache：租户可用性（状态/到期）")
class TenantStatusCacheTest {

    private TenantStatusCache cacheWith(Tenant tenant) {
        TenantDao dao = mock(TenantDao.class);
        when(dao.getByCode(anyString())).thenReturn(tenant);
        TenantStatusCache cache = new TenantStatusCache();
        ReflectionTestUtils.setField(cache, "tenantDao", dao);
        return cache;
    }

    @Test
    @DisplayName("using 且未到期 → 可用")
    void usableWhenUsingAndNotExpired() {
        Tenant t = new Tenant();
        t.setStatus("using");
        t.setExpireDate(null);
        assertThat(cacheWith(t).isUsable("t1")).isTrue();
    }

    @Test
    @DisplayName("using 且到期日为将来 → 可用")
    void usableWhenNotYetExpired() {
        Tenant t = new Tenant();
        t.setStatus("using");
        t.setExpireDate(LocalDate.now().plusDays(1));
        assertThat(cacheWith(t).isUsable("t1")).isTrue();
    }

    @Test
    @DisplayName("disabled → 不可用")
    void unusableWhenDisabled() {
        Tenant t = new Tenant();
        t.setStatus("disabled");
        assertThat(cacheWith(t).isUsable("t1")).isFalse();
    }

    @Test
    @DisplayName("已过期 → 不可用")
    void unusableWhenExpired() {
        Tenant t = new Tenant();
        t.setStatus("using");
        t.setExpireDate(LocalDate.now().minusDays(1));
        assertThat(cacheWith(t).isUsable("t1")).isFalse();
    }

    @Test
    @DisplayName("租户不存在 → 不可用")
    void unusableWhenMissing() {
        assertThat(cacheWith(null).isUsable("t1")).isFalse();
    }

    @Test
    @DisplayName("空编码（无租户约束）→ 不拦截")
    void blankTenantNotRestricted() {
        assertThat(cacheWith(null).isUsable("  ")).isTrue();
    }
}