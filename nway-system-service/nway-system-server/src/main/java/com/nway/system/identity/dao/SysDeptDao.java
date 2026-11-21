package com.nway.system.identity.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.spring.jdbc.sql.permission.WhereCondition;
import com.nway.system.identity.entity.SysDept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Repository
public class SysDeptDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public SysDept queryById(String id) {
        return sqlExecutor.queryById(id, SysDept.class);
    }

    public List<SysDept> queryByIds(Collection<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return sqlExecutor.queryList(SQL.query(SysDept.class).in(SysDept::getId, ids));
    }

    public List<SysDept> queryByCodes(Collection<String> codes) {
        if (CollectionUtils.isEmpty(codes)) {
            return Collections.emptyList();
        }
        return sqlExecutor.queryList(SQL.query(SysDept.class).in(SysDept::getDeptCode, codes));
    }

    /**
     * 查询全部有效部门（按层级编码排序，天然树序）
     */
    public List<SysDept> queryAll() {
        QueryBuilder qb = SQL.query(SysDept.class)
                .eq(SysDept::getStatus, "using")
                .orderBy(SysDept::getDeptCode);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按租户查询全部有效部门（超管指定租户下拉用，普通用户由自动租户隔离兜底）
     */
    public List<SysDept> queryAllByTenantCode(String tenantCode) {
        QueryBuilder qb = SQL.query(SysDept.class)
                .eq(SysDept::getStatus, "using")
                .eq(SysDept::getTenantCode, tenantCode)
                .orderBy(SysDept::getDeptCode);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询子树（含自身）：dept_code LIKE 'code%'
     */
    public List<SysDept> querySubTree(String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return queryAll();
        }
        QueryBuilder qb = SQL.query(SysDept.class)
                .likeRight(SysDept::getDeptCode, deptCode)
                .orderBy(SysDept::getDeptCode);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询直属子部门：code 前缀匹配 + 长度 = parentCode.length + 2
     */
    public List<SysDept> queryChildren(String parentCode) {
        int childLen = (StringUtils.hasText(parentCode) ? parentCode.length() : 0) + 2;
        QueryBuilder qb = SQL.query(SysDept.class)
                .appendCondition(new WhereCondition("LENGTH(dept_code) = ?", childLen))
                .orderBy(SysDept::getDeptCode);
        if (StringUtils.hasText(parentCode)) {
            qb.likeRight(SysDept::getDeptCode, parentCode);
        }
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询同级最大编码，用于自动生成下一级编码
     */
    public String queryMaxChildCode(String parentCode) {
        int childLen = (StringUtils.hasText(parentCode) ? parentCode.length() : 0) + 2;
        QueryBuilder qb = SQL.query(SysDept.class)
                .withColumn(SysDept::getDeptCode)
                .appendCondition(new WhereCondition("LENGTH(dept_code) = ?", childLen))
                .orderByDesc(SysDept::getDeptCode);
        if (StringUtils.hasText(parentCode)) {
            qb.likeRight(SysDept::getDeptCode, parentCode);
        }
        SysDept max = sqlExecutor.queryFirst(qb);
        return max != null ? max.getDeptCode() : null;
    }

    /**
     * 统计直属子部门数（删除前校验）
     */
    public int countChildren(String deptId) {
        return sqlExecutor.count(SQL.query(SysDept.class).eq(SysDept::getParentId, deptId));
    }

    public void insert(SysDept dept) {
        sqlExecutor.insert(dept);
    }

    public void updateById(SysDept dept) {
        sqlExecutor.updateById(dept);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysDept.class);
    }
}
