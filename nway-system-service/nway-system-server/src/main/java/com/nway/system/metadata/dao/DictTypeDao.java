package com.nway.system.metadata.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.metadata.entity.DictType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class DictTypeDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(DictType dictType) {
        sqlExecutor.insert(dictType);
    }

    public void updateById(DictType dictType) {
        sqlExecutor.updateById(dictType);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, DictType.class);
    }

    public DictType queryById(String id) {
        return sqlExecutor.queryById(id, DictType.class);
    }

    public DictType queryByCode(String dictCode) {
        QueryBuilder queryBuilder = SQL.query(DictType.class).eq(DictType::getDictCode, dictCode);
        return sqlExecutor.queryFirst(queryBuilder);
    }

    public List<DictType> query(String keyword, String tenantCode) {
        QueryBuilder queryBuilder = SQL.query(DictType.class).ignoreInvalidDeep(true)
                .eq(DictType::getTenantCode, tenantCode)
                .orderByDesc(DictType::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            // 名称或编码模糊匹配；租户隔离条件由数据层自动追加，结果始终在权限范围内
            queryBuilder.and(q -> q.like(DictType::getDictName, keyword).or().like(DictType::getDictCode, keyword));
        }
        return sqlExecutor.queryList(queryBuilder);
    }

    /**
     * 校验编码是否已存在（排除自身）
     */
    public int countByCode(String dictCode, String excludeId) {
        QueryBuilder queryBuilder = SQL.query(DictType.class).eq(DictType::getDictCode, dictCode);
        if (StringUtils.hasText(excludeId)) {
            queryBuilder.ne(DictType::getId, excludeId);
        }
        return sqlExecutor.count(queryBuilder);
    }
}
