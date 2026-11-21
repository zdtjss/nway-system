package com.nway.system.identity.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.spring.jdbc.sql.builder.UpdateBuilder;
import com.nway.system.identity.entity.SysUserDept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 用户部门关系 DAO（跨部门任职）
 * <p>
 * 删除动作说明：本表存在唯一索引 uidx_user_dept(employee_id, dept_id)，该索引不区分 is_deleted，
 * 若沿用 nway-jdbc 的逻辑删除（DELETE 被改写为 update set is_deleted = true），旧行会永久占用唯一键，
 * 用户离职再入职/移除部门后再加回同一部门时，INSERT 必然抛
 * Duplicate entry 'xxx-yyy' for key 'uidx_user_dept'。纯关联表无留存价值，故统一物理删除。
 */
@Repository
public class SysUserDeptDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(SysUserDept userDept) {
        sqlExecutor.insert(userDept);
    }

    public void batchInsert(List<SysUserDept> list) {
        sqlExecutor.batchInsert(list);
    }

    /**
     * 查询用户所有任职部门
     */
    public List<SysUserDept> queryByEmployee(String employeeId) {
        QueryBuilder qb = SQL.query(SysUserDept.class)
                .eq(SysUserDept::getEmployeeId, employeeId);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询部门下所有任职用户
     */
    public List<SysUserDept> queryByDept(String deptId) {
        QueryBuilder qb = SQL.query(SysUserDept.class)
                .eq(SysUserDept::getDeptId, deptId);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 批量查询多个用户的全部任职部门（跨部门场景）
     */
    public List<SysUserDept> queryByEmployees(Collection<String> employeeIds) {
        if (CollectionUtils.isEmpty(employeeIds)) {
            return Collections.emptyList();
        }
        QueryBuilder qb = SQL.query(SysUserDept.class)
                .in(SysUserDept::getEmployeeId, employeeIds);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询用户的主部门记录
     */
    public SysUserDept queryPrimary(String employeeId) {
        QueryBuilder qb = SQL.query(SysUserDept.class)
                .eq(SysUserDept::getEmployeeId, employeeId)
                .eq(SysUserDept::getIsPrimary, true);
        return sqlExecutor.queryFirst(qb);
    }

    /**
     * 清除用户的所有主部门标记（保存前先清）
     */
    public void clearPrimary(String employeeId) {
        UpdateBuilder ub = SQL.update(SysUserDept.class)
                .set(SysUserDept::getIsPrimary, false)
                .eq(SysUserDept::getEmployeeId, employeeId)
                .eq(SysUserDept::getIsPrimary, true);
        sqlExecutor.update(ub);
    }

    /**
     * 物理删除用户与单个部门的关系（含同键的历史软删除残留行）
     * <p>
     * 不带 is_deleted 条件，使历史遗留的软删除行一并被清理，保证同一部门可重新任职。
     * 不追加租户条件：employee_id 全局唯一，调用方（UserDeptService/DeptService）
     * 已在 Service 层完成目标用户的租户管辖权校验。
     */
    public void deleteByEmployeeAndDept(String employeeId, String deptId) {
        String sql = "DELETE FROM sys_user_dept WHERE employee_id = ? AND dept_id = ?";
        sqlExecutor.getJdbcTemplate().update(sql, employeeId, deptId);
    }

    /**
     * 物理删除用户所有部门关系（含同员工的历史软删除残留行），用于全量覆盖式保存
     */
    public void deleteByEmployee(String employeeId) {
        String sql = "DELETE FROM sys_user_dept WHERE employee_id = ?";
        sqlExecutor.getJdbcTemplate().update(sql, employeeId);
    }
}
