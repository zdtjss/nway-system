package com.nway.system.authorization.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.system.authorization.entity.SysGrantTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 授权模板 DAO。
 */
@Repository
public class GrantTemplateDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public List<SysGrantTemplate> list() {
        return sqlExecutor.queryList(SQL.query(SysGrantTemplate.class)
                .orderBy(SysGrantTemplate::getTemplateCode));
    }

    public SysGrantTemplate getById(String id) {
        return sqlExecutor.queryById(id, SysGrantTemplate.class);
    }

    public SysGrantTemplate getByCode(String code) {
        return sqlExecutor.queryOne(SQL.query(SysGrantTemplate.class)
                .eq(SysGrantTemplate::getTemplateCode, code));
    }

    public void insert(SysGrantTemplate template) {
        sqlExecutor.insert(template);
    }

    public void updateById(SysGrantTemplate template) {
        sqlExecutor.updateById(template);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysGrantTemplate.class);
    }
}
