package com.nway.system.common.persistence;

import com.nway.spring.jdbc.sql.SqlType;
import com.nway.spring.jdbc.sql.fill.FillStrategy;

import java.time.LocalDateTime;

/**
 * 适用于update_time，插入和修改时都支持
 */
public class LocalDateTimeNow implements FillStrategy {

    @Override
    public boolean isSupport(SqlType sqlType) {
        return SqlType.INSERT == sqlType || SqlType.UPDATE == sqlType;
    }

    @Override
    public Object getValue(SqlType sqlType, Object val) {
        if (val == DEFAULT_NONE || val == null) {
            return LocalDateTime.now();
        }
        return val;
    }

}
