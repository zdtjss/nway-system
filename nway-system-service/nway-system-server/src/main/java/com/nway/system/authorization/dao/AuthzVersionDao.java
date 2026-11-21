package com.nway.system.authorization.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.system.authorization.entity.SysAuthzVersion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 授权版本 DAO。
 */
@Repository
public class AuthzVersionDao {

    private final SqlExecutor sqlExecutor;
    private final JdbcTemplate jdbcTemplate;

    public AuthzVersionDao(SqlExecutor sqlExecutor, JdbcTemplate jdbcTemplate) {
        this.sqlExecutor = sqlExecutor;
        this.jdbcTemplate = jdbcTemplate;
    }

    public SysAuthzVersion getGlobal() {
        return sqlExecutor.queryById(SysAuthzVersion.GLOBAL_ID, SysAuthzVersion.class);
    }

    /**
     * 原子递增全局版本，返回递增后的值。
     * <p>
     * 必须是单条 {@code UPDATE ... SET version = version + 1}：旧实现是
     * “读出来 + 1 再写回”，多实例并发时后写者会覆盖前者的递增，导致
     * <b>版本号回退</b>，消费方按版本失效的缓存就不会刷新（撤权不生效）。
     * <p>
     * 行不存在时补插；并发补插时由主键冲突兜住，回退为再执行一次 UPDATE。
     */
    public long incrementAndGet() {
        String updateSql = "update sys_authz_version "
                + "set version = version + 1, updated_time = now() where id = ?";
        if (jdbcTemplate.update(updateSql, SysAuthzVersion.GLOBAL_ID) == 0) {
            try {
                jdbcTemplate.update("insert into sys_authz_version (id, scope, version, updated_time) "
                                + "values (?, ?, 2, now())",
                        SysAuthzVersion.GLOBAL_ID, SysAuthzVersion.GLOBAL_SCOPE);
            } catch (DuplicateKeyException e) {
                // 另一个实例刚插入，再走一次递增
                jdbcTemplate.update(updateSql, SysAuthzVersion.GLOBAL_ID);
            }
        }
        Long version = jdbcTemplate.queryForObject(
                "select version from sys_authz_version where id = ?",
                Long.class, SysAuthzVersion.GLOBAL_ID);
        return version == null ? 1L : version;
    }
}
