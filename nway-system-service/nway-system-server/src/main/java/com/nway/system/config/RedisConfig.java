package com.nway.system.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * redis 配置
 */
@Configuration
public class RedisConfig {

    @Autowired
    private RedisConnectionFactory factory;

    @Value("${spring.data.redis.host}")
    private String redisAddress;

    @Bean
    public <T> RedisTemplate<String, T> redisTemplate() {

        JacksonJsonRedisSerializer<Object> serializer = new JacksonJsonRedisSerializer<>(Object.class);

        RedisTemplate<String, T> redisTemplate = new RedisTemplate<>();

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(serializer);
        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setConnectionFactory(factory);
        return redisTemplate;
    }

    @Bean
    public <T> HashOperations<String, String, T> hashOperations(RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForHash();
    }

    @Bean
    public <T> ValueOperations<String, T> valueOperations(RedisTemplate<String, T> redisTemplate) {
        return redisTemplate.opsForValue();
    }

    @Bean
    public ListOperations<String, Object> listOperations(RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForList();
    }

    @Bean
    public SetOperations<String, Object> setOperations(RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForSet();
    }

    @Bean
    public ZSetOperations<String, Object> zSetOperations(RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForZSet();
    }

    /**
     * Redis 消息监听容器（Pub/Sub 基础设施）。
     * <p>
     * 供 {@link com.nway.system.identity.service.TokenVersionPubSubService} 订阅 Token 版本号变更通知。
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

}
