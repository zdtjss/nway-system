package com.nway.system.authorization.service.grant;

import com.nway.system.authorization.dao.AuthzVersionDao;
import com.nway.system.authorization.entity.SysAuthzVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 授权版本：递增走原子 SQL，本地缓存 2 秒。
 */
class AuthzVersionServiceTest {

    private final AuthzVersionDao versionDao = mock(AuthzVersionDao.class);
    private final AuthzVersionService service = new AuthzVersionService(versionDao);

    @Test
    @DisplayName("bump 走原子递增并立即刷新本地缓存")
    void bumpUsesAtomicIncrementAndRefreshesCache() {
        when(versionDao.incrementAndGet()).thenReturn(9L);

        assertThat(service.bump()).isEqualTo(9L);
        // 刚 bump 过，缓存应直接命中，不再回库读
        assertThat(service.current()).isEqualTo(9L);
        verify(versionDao, never()).getGlobal();
        verify(versionDao, times(1)).incrementAndGet();
    }

    @Test
    @DisplayName("current 本地缓存 2 秒：连续读取只回库一次")
    void currentCachesForTwoSeconds() {
        SysAuthzVersion version = new SysAuthzVersion();
        version.setVersion(5L);
        when(versionDao.getGlobal()).thenReturn(version);

        assertThat(service.current()).isEqualTo(5L);
        assertThat(service.current()).isEqualTo(5L);
        verify(versionDao, times(1)).getGlobal();
    }

    @Test
    @DisplayName("库不可用时沿用上次版本（缓存不被清空）")
    void currentFallsBackToLastKnownVersion() {
        SysAuthzVersion version = new SysAuthzVersion();
        version.setVersion(6L);
        when(versionDao.getGlobal()).thenReturn(version);
        assertThat(service.current()).isEqualTo(6L);

        when(versionDao.getGlobal()).thenThrow(new RuntimeException("db down"));
        // 等过本地缓存窗口，强制回库失败
        sleepQuietly(2_100);
        assertThat(service.current()).isEqualTo(6L);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
