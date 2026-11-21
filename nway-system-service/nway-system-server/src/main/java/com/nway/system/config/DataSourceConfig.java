package com.nway.system.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class DataSourceConfig {

    @Component("druidDataSource")
    @ConfigurationProperties("spring.datasource.druid")
    class DruidDataSourceWrapper extends DruidDataSource {

    }
}
