package com.nway.system.identity.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.identity.entity.SysPosition;
import com.nway.system.identity.web.model.PositionQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 岗位 DAO
 */
@Repository
public class SysPositionDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(SysPosition position) {
        sqlExecutor.insert(position);
    }

    public void updateById(SysPosition position) {
        sqlExecutor.updateById(position);
    }

    public SysPosition queryById(String id) {
        return sqlExecutor.queryById(id, SysPosition.class);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysPosition.class);
    }

    /**
     * 分页查询岗位列表（编码/名称支持模糊检索）
     */
    public Page<SysPosition> queryPage(PositionQuery query) {
        QueryBuilder qb = SQL.query(SysPosition.class).ignoreInvalidDeep(true)
                .eq(SysPosition::getStatus, query.getStatus())
                .like(SysPosition::getPositionCode, query.getPositionCode())
                .like(SysPosition::getPositionName, query.getPositionName())
                .orderBy(SysPosition::getPositionLevel);
        return sqlExecutor.queryPage(qb, query.getCurrent(), query.getSize());
    }

    /**
     * 查询所有启用岗位
     */
    public List<SysPosition> queryAll() {
        QueryBuilder qb = SQL.query(SysPosition.class)
                .eq(SysPosition::getStatus, "using")
                .orderBy(SysPosition::getPositionLevel);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按状态查询岗位列表
     */
    public List<SysPosition> queryByStatus(String status) {
        QueryBuilder qb = SQL.query(SysPosition.class)
                .eq(SysPosition::getStatus, status)
                .orderBy(SysPosition::getPositionLevel);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按编码查询
     */
    public SysPosition queryByCode(String positionCode) {
        return sqlExecutor.queryFirst(SQL.query(SysPosition.class)
                .eq(SysPosition::getPositionCode, positionCode));
    }

    /**
     * 查询部门下的岗位（删除部门前校验、部门岗位列表用）
     */
    public List<SysPosition> queryByDept(String deptId) {
        QueryBuilder qb = SQL.query(SysPosition.class)
                .eq(SysPosition::getDeptId, deptId);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 批量按编码查询
     */
    public List<SysPosition> queryByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }
        QueryBuilder qb = SQL.query(SysPosition.class)
                .in(SysPosition::getPositionCode, codes);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 批量按 ID 查询
     */
    public List<SysPosition> queryByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return sqlExecutor.queryList(SQL.query(SysPosition.class).in(SysPosition::getId, ids));
    }
}
