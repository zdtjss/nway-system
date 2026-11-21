package com.nway.system.tenant.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户级请求配额守卫测试。
 */
class TenantQuotaGuardTest {

    @Test
    @DisplayName("未启用配额（<=0）时全部放行")
    void disabledQuotaAllowsAll() {
        TenantQuotaGuard guard = new TenantQuotaGuard(0);
        for (int i = 0; i < 100; i++) {
            assertTrue(guard.tryAcquire("t1"));
        }
    }

    @Test
    @DisplayName("租户为空时不限流")
    void blankTenantAllowsAll() {
        TenantQuotaGuard guard = new TenantQuotaGuard(1);
        assertTrue(guard.tryAcquire(null));
        assertTrue(guard.tryAcquire(""));
    }

    @Test
    @DisplayName("同一租户超出窗口配额后拒绝，其他租户不受影响")
    void quotaIsPerTenant() {
        TenantQuotaGuard guard = new TenantQuotaGuard(2);
        assertTrue(guard.tryAcquire("t1"));
        assertTrue(guard.tryAcquire("t1"));
        assertFalse(guard.tryAcquire("t1"), "超出配额应拒绝");
        // 其他租户独立计数
        assertTrue(guard.tryAcquire("t2"));
    }
}