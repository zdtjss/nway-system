package com.nway.system.config;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.system.common.util.SpringContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

@Configuration
public class CommonConfig {

    @Bean
    public SpringContext springContext() {
        return new SpringContext();
    }

    @Bean
    @DependsOn("druidDataSource")
    public SqlExecutor createSqlExecutor(DataSource dataSource) {
        return new SqlExecutor(dataSource);
    }

}
