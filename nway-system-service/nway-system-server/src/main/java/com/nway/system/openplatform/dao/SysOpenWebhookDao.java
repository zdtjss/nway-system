package com.nway.system.openplatform.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.openplatform.entity.SysOpenWebhook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 开放平台 Webhook DAO。
 *
 * @author nway
 */
@Repository
public class SysOpenWebhookDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(SysOpenWebhook webhook) {
        sqlExecutor.insert(webhook);
    }

    public void updateById(SysOpenWebhook webhook) {
        sqlExecutor.updateById(webhook);
    }

    public SysOpenWebhook getById(String id) {
        return sqlExecutor.queryById(id, SysOpenWebhook.class);
    }

    public List<SysOpenWebhook> queryByApp(String appId) {
        QueryBuilder qb = SQL.query(SysOpenWebhook.class)
                .eq(SysOpenWebhook::getAppId, appId);
        return sqlExecutor.queryList(qb);
    }

    public List<SysOpenWebhook> queryByEvent(String eventType) {
        QueryBuilder qb = SQL.query(SysOpenWebhook.class)
                .eq(SysOpenWebhook::getEventType, eventType)
                .eq(SysOpenWebhook::getStatus, "using");
        return sqlExecutor.queryList(qb);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysOpenWebhook.class);
    }
}
