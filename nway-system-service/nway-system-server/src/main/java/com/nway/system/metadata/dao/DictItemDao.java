package com.nway.system.metadata.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.spring.jdbc.sql.permission.WhereCondition;
import com.nway.system.metadata.entity.DictItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class DictItemDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(DictItem dictItem) {
        sqlExecutor.insert(dictItem);
    }

    public void updateById(DictItem dictItem) {
        sqlExecutor.updateById(dictItem);
    }

    public void batchInsert(List<DictItem> dictItems) {
        sqlExecutor.batchInsert(dictItems);
    }

    public void batchUpdateById(List<DictItem> dictItems) {
        sqlExecutor.batchUpdateById(dictItems);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, DictItem.class);
    }

    public DictItem queryById(String id) {
        return sqlExecutor.queryById(id, DictItem.class);
    }

    /**
     * 按字典类别查询字典项，支持对 item_value JSON 内字段过滤
     *
     * @param likeFilters  模糊匹配过滤（字段编码 → 过滤值）
     * @param exactFilters 精确匹配过滤（字段编码 → 过滤值）
     */
    public List<DictItem> queryByTypeId(String dictTypeId, String status,
                                        Map<String, String> likeFilters, Map<String, String> exactFilters) {
        QueryBuilder queryBuilder = SQL.query(DictItem.class)
                .ignoreInvalidDeep(true)
                .eq(DictItem::getDictTypeId, dictTypeId)
                .eq(DictItem::getStatus, status);
        appendJsonFilters(queryBuilder, likeFilters, true);
        appendJsonFilters(queryBuilder, exactFilters, false);
        queryBuilder.orderBy(DictItem::getSort, DictItem::getCreateTime);
        return sqlExecutor.queryList(queryBuilder);
    }

    /**
     * 追加 item_value JSON 字段过滤条件，字段编码已由 Service 层正则校验，值走占位符
     */
    private void appendJsonFilters(QueryBuilder queryBuilder, Map<String, String> filters, boolean like) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        filters.forEach((fieldCode, value) -> {
            if (value == null || value.isEmpty()) {
                return;
            }
            String expr = "JSON_UNQUOTE(JSON_EXTRACT(item_value, '$." + fieldCode + "')) " + (like ? "like" : "=") + " ?";
            Object param = like ? "%" + value + "%" : value;
            queryBuilder.appendCondition(new WhereCondition(expr, param));
        });
    }

    public int countByTypeId(String dictTypeId) {
        QueryBuilder queryBuilder = SQL.query(DictItem.class).eq(DictItem::getDictTypeId, dictTypeId);
        return sqlExecutor.count(queryBuilder);
    }
}
