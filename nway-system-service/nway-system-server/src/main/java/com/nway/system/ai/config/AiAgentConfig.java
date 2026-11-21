package com.nway.system.ai.config;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.agentscope.harness.agent.DistributedStore;
import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.RedisClusterClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import javax.sql.DataSource;

/**
 * AgentScope 状态存储：复用 Spring 的 Lettuce 客户端，会话状态写 Redis。
 * <p>
 * 不关闭 RedisAgentStateStore：底层 RedisClient 由 Spring 管理，重复关闭会影响其他组件。
 *
 * @author nway
 */
@Configuration
public class AiAgentConfig {

    @Bean(destroyMethod = "")
    public AgentStateStore aiAgentStateStore(LettuceConnectionFactory connectionFactory,
                                             @Value("${nway.ai.redis-key-prefix:nway:ai:}") String keyPrefix) {
        Object nativeClient = connectionFactory.getNativeClient();
        RedisAgentStateStore.Builder builder = RedisAgentStateStore.builder().keyPrefix(keyPrefix);
        if (nativeClient instanceof RedisClient client) {
            builder.lettuceClient(client);
        } else if (nativeClient instanceof RedisClusterClient clusterClient) {
            builder.lettuceClusterClient(clusterClient);
        } else {
            throw new IllegalStateException("无法获取 Lettuce RedisClient，AI 会话状态无法持久化");
        }
        return builder.build();
    }

    @Bean
    public DistributedStore aiDistributedStore(DataSource dataSource) {
        return JdbcDistributedStore.create(dataSource);
    }
}
