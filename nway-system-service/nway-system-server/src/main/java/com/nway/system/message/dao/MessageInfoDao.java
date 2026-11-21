package com.nway.system.message.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.message.web.model.MessageQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class MessageInfoDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(MessageInfo message) {
        sqlExecutor.insert(message);
    }

    public MessageInfo selectById(Long id) {
        return sqlExecutor.queryById(String.valueOf(id), MessageInfo.class);
    }

    public List<MessageInfo> query(MessageQuery query) {
        QueryBuilder queryBuilder = SQL.query(MessageInfo.class).ignoreInvalidDeep(true)
                .like(MessageInfo::getTitle, query.getTitle())
                .like(MessageInfo::getContent, query.getContent())
                .eq(MessageInfo::getSenderId, query.getSenderId())
                .between(MessageInfo::getCreateTime, query.getCreateTimeBegin(), query.getCreateTimeEnd());
        return sqlExecutor.queryList(queryBuilder);
    }

    public Page<MessageInfo> queryPage(MessageQuery query) {
        QueryBuilder queryBuilder = SQL.query(MessageInfo.class).ignoreInvalidDeep(true)
                .like(MessageInfo::getTitle, query.getTitle())
                .like(MessageInfo::getContent, query.getContent())
                .eq(MessageInfo::getSenderId, query.getSenderId())
                .like(MessageInfo::getSenderName, query.getSenderName())
                .between(MessageInfo::getCreateTime, query.getCreateTimeBegin(), query.getCreateTimeEnd());
        return sqlExecutor.queryPage(queryBuilder, query.getCurrent(), query.getSize());
    }

    public Map<String, MessageInfo> queryByIds(Collection<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        return sqlExecutor.queryListMap(SQL.query(MessageInfo.class).in(MessageInfo::getId, ids), MessageInfo::getId);
    }

    public List<MessageInfo> selectList(QueryBuilder queryBuilder) {
        return sqlExecutor.queryList(queryBuilder);
    }
}
