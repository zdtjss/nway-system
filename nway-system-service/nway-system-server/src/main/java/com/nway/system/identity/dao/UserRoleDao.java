package com.nway.system.identity.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.DeleteBuilder;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.identity.web.model.UserRoleQuery;
import com.nway.system.identity.entity.Role;
import com.nway.system.identity.entity.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Repository
public class UserRoleDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(UserRole userRole) {
        sqlExecutor.insert(userRole);
    }

    public void insertBatch(List<UserRole> userRoleList) {
        sqlExecutor.batchInsert(userRoleList);
    }

    public List<UserRole> queryByUser(List<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return List.of();
        }
        QueryBuilder queryBuilder = SQL.query(UserRole.class).in(UserRole::getEmployeeId, userIds);
        return sqlExecutor.queryList(queryBuilder);
    }

    public List<UserRole> queryByRole(List<String> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        QueryBuilder queryBuilder = SQL.query(UserRole.class).in(UserRole::getRoleId, roleIds);
        return sqlExecutor.queryList(queryBuilder);
    }

    public List<UserRole> query(UserRoleQuery userRoleDTO) {
        QueryBuilder queryBuilder = SQL.query(UserRole.class).ignoreInvalidDeep(true)
                .eq(UserRole::getRoleId, userRoleDTO.getRoleId())
                .eq(UserRole::getEmployeeId, userRoleDTO.getEmployeeId());
        return sqlExecutor.queryList(queryBuilder);
    }

    public void updateById(UserRole userRole) {
        sqlExecutor.updateById(userRole);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, UserRole.class);
    }

    public void unbindUserRole(String employeeId) {
        DeleteBuilder deleteBuilder = SQL.delete(UserRole.class)
                .eq(UserRole::getEmployeeId, employeeId);
        sqlExecutor.delete(deleteBuilder);
    }

    /**
     * 删除角色下全部用户关系（按角色保存前清空）
     */
    public void deleteByRole(String roleId) {
        DeleteBuilder deleteBuilder = SQL.delete(UserRole.class)
                .eq(UserRole::getRoleId, roleId);
        sqlExecutor.delete(deleteBuilder);
    }

    public Page<Role> queryPage(UserRoleQuery userRoleDTO) {
        QueryBuilder queryBuilder = SQL.query(UserRole.class).ignoreInvalidDeep(true)
                .eq(UserRole::getRoleId, userRoleDTO.getRoleId())
                .eq(UserRole::getEmployeeId, userRoleDTO.getEmployeeId());
        return sqlExecutor.queryPage(queryBuilder, userRoleDTO.getCurrent(), userRoleDTO.getSize());
    }
}
