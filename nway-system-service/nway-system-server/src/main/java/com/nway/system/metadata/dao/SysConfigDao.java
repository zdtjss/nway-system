package com.nway.system.metadata.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.metadata.entity.SysConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class SysConfigDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(SysConfig config) {
        sqlExecutor.insert(config);
    }

    public void updateById(SysConfig config) {
        sqlExecutor.updateById(config);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysConfig.class);
    }

    public SysConfig queryById(String id) {
        return sqlExecutor.queryById(id, SysConfig.class);
    }

    public Page<SysConfig> queryPage(String configKey, String configName, String configGroup, String status, int page, int pageSize) {
        QueryBuilder queryBuilder = SQL.query(SysConfig.class).ignoreInvalidDeep(true)
                .like(SysConfig::getConfigKey, configKey)
                .like(SysConfig::getConfigName, configName)
                .eq(SysConfig::getConfigGroup, configGroup)
                .eq(SysConfig::getStatus, status)
                .orderByDesc(SysConfig::getCreateTime);
        return sqlExecutor.queryPage(queryBuilder, page, pageSize);
    }

    public SysConfig getByKey(String configKey) {
        QueryBuilder queryBuilder = SQL.query(SysConfig.class).eq(SysConfig::getConfigKey, configKey);
        return sqlExecutor.queryFirst(queryBuilder);
    }

    /**
     * 校验配置键是否已存在（排除自身）
     */
    public int countByKey(String configKey, String excludeId) {
        QueryBuilder queryBuilder = SQL.query(SysConfig.class).eq(SysConfig::getConfigKey, configKey);
        if (StringUtils.hasText(excludeId)) {
            queryBuilder.ne(SysConfig::getId, excludeId);
        }
        return sqlExecutor.count(queryBuilder);
    }
}
