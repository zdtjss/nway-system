package com.nway.system.openplatform.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.openplatform.entity.SysOpenApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 开放平台应用 DAO。
 *
 * @author nway
 */
@Repository
public class SysOpenAppDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(SysOpenApp app) {
        sqlExecutor.insert(app);
    }

    public void updateById(SysOpenApp app) {
        sqlExecutor.updateById(app);
    }

    public SysOpenApp getById(String id) {
        return sqlExecutor.queryById(id, SysOpenApp.class);
    }

    public SysOpenApp queryByCode(String appCode) {
        QueryBuilder qb = SQL.query(SysOpenApp.class)
                .eq(SysOpenApp::getAppCode, appCode);
        return sqlExecutor.queryFirst(qb);
    }

    public List<SysOpenApp> queryAll() {
        QueryBuilder qb = SQL.query(SysOpenApp.class);
        return sqlExecutor.queryList(qb);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysOpenApp.class);
    }
}
