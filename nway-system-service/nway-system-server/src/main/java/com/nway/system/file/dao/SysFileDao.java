package com.nway.system.file.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.file.entity.SysFile;
import com.nway.system.file.web.model.FileQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * 文件元数据 DAO。
 * <p>
 * 实体继承 {@code BaseEntity}，查询/更新/删除自动追加租户隔离与逻辑删除条件。
 *
 * @author nway
 */
@Repository
public class SysFileDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(SysFile file) {
        sqlExecutor.insert(file);
    }

    public SysFile queryById(String id) {
        return sqlExecutor.queryById(id, SysFile.class);
    }

    public void deleteById(String id) {
        sqlExecutor.deleteById(id, SysFile.class);
    }

    public Page<SysFile> queryPage(FileQuery query) {
        QueryBuilder queryBuilder = SQL.query(SysFile.class).ignoreInvalidDeep(true)
                .like(SysFile::getFileName, query.getFileName())
                .eq(SysFile::getBizType, query.getBizType())
                .eq(SysFile::getCreatorId, query.getCreatorId())
                .ge(SysFile::getCreateTime, query.getStartTime())
                .le(SysFile::getCreateTime, query.getEndTime())
                .orderByDesc(SysFile::getCreateTime);
        return sqlExecutor.queryPage(queryBuilder, query.getCurrent(), query.getSize());
    }
}
