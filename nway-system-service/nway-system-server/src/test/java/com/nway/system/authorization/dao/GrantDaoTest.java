package com.nway.system.authorization.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.system.authorization.entity.SysGrant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 授权写入的唯一键语义：{@code uidx_grant_subject_resource} 不含 is_deleted，
 * 逻辑删除后的行仍占键，重新授权必须复用墓碑行（D5）。
 */
@DisplayName("GrantDao：墓碑行复用（D5）")
class GrantDaoTest {

    private SqlExecutor sqlExecutor;
    private GrantDao grantDao;

    @BeforeEach
    void setUp() {
        sqlExecutor = mock(SqlExecutor.class);
        grantDao = new GrantDao();
        ReflectionTestUtils.setField(grantDao, "sqlExecutor", sqlExecutor);
    }

    @Test
    @DisplayName("同键墓碑行存在：复活（保留 id、version+1、is_deleted=0），不再 INSERT")
    void revivesTombstoneInsteadOfInsert() {
        SysGrant tombstone = new SysGrant();
        tombstone.setId("g1");
        tombstone.setVersion(3L);
        tombstone.setIsDeleted(Boolean.TRUE);
        when(sqlExecutor.queryOne(any())).thenReturn(tombstone);

        SysGrant grant = newGrant();

        grantDao.insertOrRevive(grant);

        assertThat(grant.getId()).isEqualTo("g1");
        assertThat(grant.getVersion()).isEqualTo(4L);
        assertThat(grant.getIsDeleted()).isFalse();
        // 复活必须走 UpdateBuilder（可绕行级策略），updateById 的 WHERE 会永远命中不到墓碑行
        verify(sqlExecutor).update(any());
        verify(sqlExecutor, never()).updateById(any());
        verify(sqlExecutor, never()).insert(any());
    }

    @Test
    @DisplayName("无墓碑行：正常 INSERT")
    void insertsWhenNoTombstone() {
        when(sqlExecutor.queryOne(any())).thenReturn(null);

        SysGrant grant = newGrant();

        grantDao.insertOrRevive(grant);

        verify(sqlExecutor).insert(grant);
        verify(sqlExecutor, never()).update(any());
        verify(sqlExecutor, never()).updateById(any());
    }

    private static SysGrant newGrant() {
        SysGrant grant = new SysGrant();
        grant.setSubjectType("ROLE");
        grant.setSubjectId("r1");
        grant.setAppCode("wform");
        grant.setResourceCode("wform:data");
        grant.setEffect(SysGrant.EFFECT_ALLOW);
        grant.setActions("[\"read\"]");
        grant.setDataScope(SysGrant.SCOPE_ALL);
        grant.setStatus(SysGrant.STATUS_ACTIVE);
        grant.setTenantCode("t1");
        grant.setVersion(1L);
        return grant;
    }
}
