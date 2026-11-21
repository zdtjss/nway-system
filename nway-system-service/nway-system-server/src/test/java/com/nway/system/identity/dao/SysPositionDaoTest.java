package com.nway.system.identity.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.identity.web.model.PositionQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 岗位分页查询条件（D17）：编码/名称支持模糊检索，空条件不参与拼接。
 */
@DisplayName("SysPositionDao：岗位编码/名称检索（D17）")
class SysPositionDaoTest {

    private SqlExecutor sqlExecutor;
    private SysPositionDao positionDao;

    @BeforeEach
    void setUp() {
        sqlExecutor = mock(SqlExecutor.class);
        positionDao = new SysPositionDao();
        ReflectionTestUtils.setField(positionDao, "sqlExecutor", sqlExecutor);
        // 超管上下文：跳过租户隔离条件，断言只聚焦检索条件
        LoginUser user = new LoginUser();
        user.setEmployeeId("e001");
        user.setTenantCode("default");
        user.setSuperAdmin(true);
        LoginUserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clean();
    }

    @Test
    @DisplayName("编码/名称非空：生成 like 条件并携带 % 通配符")
    void queryPageAppliesLikeConditions() {
        PositionQuery query = new PositionQuery();
        query.setPositionCode("gl");
        query.setPositionName("管理");

        QueryBuilder qb = captureQueryBuilder(query);

        assertThat(qb.getSql())
                .contains("position_code like ?")
                .contains("position_name like ?");
        assertThat(qb.getParam()).contains("%gl%", "%管理%");
    }

    @Test
    @DisplayName("编码/名称为空：不生成 like 条件，状态筛选保持生效")
    void queryPageIgnoresBlankConditions() {
        PositionQuery query = new PositionQuery();
        query.setStatus("using");

        QueryBuilder qb = captureQueryBuilder(query);

        String sql = qb.getSql();
        assertThat(sql).contains("status = ?");
        assertThat(sql).doesNotContain("like ?");
        assertThat(qb.getParam()).contains("using");
        assertThat(qb.getParam()).noneMatch(param -> param instanceof String str && str.startsWith("%"));
    }

    private QueryBuilder captureQueryBuilder(PositionQuery query) {
        positionDao.queryPage(query);
        ArgumentCaptor<QueryBuilder> captor = ArgumentCaptor.forClass(QueryBuilder.class);
        verify(sqlExecutor).queryPage(captor.capture(), anyInt(), anyInt());
        return captor.getValue();
    }
}
