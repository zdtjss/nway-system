package com.nway.system.identity.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.DeleteBuilder;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.spring.jdbc.sql.builder.UpdateBuilder;
import com.nway.system.identity.entity.SysUserPosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户岗位关联 DAO
 */
@Repository
public class SysUserPositionDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(SysUserPosition userPosition) {
        sqlExecutor.insert(userPosition);
    }

    public void batchInsert(List<SysUserPosition> list) {
        sqlExecutor.batchInsert(list);
    }

    /**
     * 查询用户所有岗位
     */
    public List<SysUserPosition> queryByEmployee(String employeeId) {
        QueryBuilder qb = SQL.query(SysUserPosition.class)
                .eq(SysUserPosition::getEmployeeId, employeeId);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询某岗位下所有用户
     */
    public List<SysUserPosition> queryByPosition(String positionId) {
        QueryBuilder qb = SQL.query(SysUserPosition.class)
                .eq(SysUserPosition::getPositionId, positionId);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询某部门某岗位的用户（审批找人场景）
     */
    public List<SysUserPosition> queryByPositionAndDept(String positionId, String deptId) {
        QueryBuilder qb = SQL.query(SysUserPosition.class)
                .eq(SysUserPosition::getPositionId, positionId)
                .eq(SysUserPosition::getDeptId, deptId);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询用户在指定部门的岗位关系
     */
    public List<SysUserPosition> queryByEmployeeAndDept(String employeeId, String deptId) {
        QueryBuilder qb = SQL.query(SysUserPosition.class)
                .eq(SysUserPosition::getEmployeeId, employeeId)
                .eq(SysUserPosition::getDeptId, deptId);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询用户在指定岗位的关联记录
     */
    public SysUserPosition queryByEmployeeAndPosition(String employeeId, String positionId) {
        return sqlExecutor.queryFirst(SQL.query(SysUserPosition.class)
                .eq(SysUserPosition::getEmployeeId, employeeId)
                .eq(SysUserPosition::getPositionId, positionId));
    }

    /**
     * 查询某部门下的所有岗位关系（删除部门前校验用）
     */
    public List<SysUserPosition> queryByDept(String deptId) {
        QueryBuilder qb = SQL.query(SysUserPosition.class)
                .eq(SysUserPosition::getDeptId, deptId);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询用户的主岗记录
     */
    public SysUserPosition queryPrimary(String employeeId) {
        QueryBuilder qb = SQL.query(SysUserPosition.class)
                .eq(SysUserPosition::getEmployeeId, employeeId)
                .eq(SysUserPosition::getIsPrimary, true);
        return sqlExecutor.queryFirst(qb);
    }

    /**
     * 清除用户的主岗标记
     */
    public void clearPrimary(String employeeId) {
        UpdateBuilder ub = SQL.update(SysUserPosition.class)
                .set(SysUserPosition::getIsPrimary, false)
                .eq(SysUserPosition::getEmployeeId, employeeId)
                .eq(SysUserPosition::getIsPrimary, true);
        sqlExecutor.update(ub);
    }

    /**
     * 删除用户在指定部门的岗位关系（移出部门联动清理）
     */
    public void deleteByEmployeeAndDept(String employeeId, String deptId) {
        DeleteBuilder db = SQL.delete(SysUserPosition.class)
                .eq(SysUserPosition::getEmployeeId, employeeId)
                .eq(SysUserPosition::getDeptId, deptId);
        sqlExecutor.delete(db);
    }

    /**
     * 删除用户在指定岗位的关联记录
     */
    public void deleteByEmployeeAndPosition(String employeeId, String positionId) {
        DeleteBuilder db = SQL.delete(SysUserPosition.class)
                .eq(SysUserPosition::getEmployeeId, employeeId)
                .eq(SysUserPosition::getPositionId, positionId);
        sqlExecutor.delete(db);
    }

    /**
     * 更新岗位关系（变更主部门时迁移主岗用）
     */
    public void updateById(SysUserPosition userPosition) {
        sqlExecutor.updateById(userPosition);
    }

    /**
     * 删除用户所有岗位关联
     */
    public void deleteByEmployee(String employeeId) {
        DeleteBuilder db = SQL.delete(SysUserPosition.class)
                .eq(SysUserPosition::getEmployeeId, employeeId);
        sqlExecutor.delete(db);
    }

    /**
     * 删除指定记录
     */
    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysUserPosition.class);
    }
}
