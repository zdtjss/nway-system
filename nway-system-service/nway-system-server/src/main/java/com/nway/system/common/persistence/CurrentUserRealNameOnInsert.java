package com.nway.system.common.persistence;

import com.nway.spring.jdbc.sql.SqlType;
import com.nway.spring.jdbc.sql.fill.FillStrategy;
import com.nway.system.common.security.LoginUserContext;

/**
 * 适用于update_time，插入和修改时都支持
 */
public class CurrentUserRealNameOnInsert implements FillStrategy {

    @Override
    public boolean isSupport(SqlType sqlType) {
        return SqlType.INSERT == sqlType;
    }

    @Override
    public Object getValue(SqlType sqlType, Object val) {
        if (val == DEFAULT_NONE || val == null) {
            return LoginUserContext.current().getRealName();
        }
        return val;
    }

}
