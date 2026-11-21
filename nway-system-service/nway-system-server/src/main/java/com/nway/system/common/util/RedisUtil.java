package com.nway.system.common.util;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Component
public class RedisUtil {

    @Autowired
    private ValueOperations<String, String> valueOperations;

    public <T> T get(String key, Class<T> type) {
        return JSON.parseObject(valueOperations.get(key), type);
    }
}
