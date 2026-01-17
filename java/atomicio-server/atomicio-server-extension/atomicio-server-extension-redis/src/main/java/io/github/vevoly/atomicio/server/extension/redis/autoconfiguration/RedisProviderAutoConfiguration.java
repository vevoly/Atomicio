package io.github.vevoly.atomicio.server.extension.redis.autoconfiguration;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import io.github.vevoly.atomicio.server.extension.redis.cluster.RedisClusterProvider;
import io.github.vevoly.atomicio.server.extension.redis.state.RedisStateProvider;
import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX_CLUSTER, name = "enabled", havingValue = "true")
public class RedisProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AtomicIOClusterProvider.class)
    public AtomicIOClusterProvider redisClusterProvider(AtomicIOProperties config, RedisClient redisClient) {
        log.info("AtomicIO: 启用 Redis Cluster Provider (集群模式)");
        return new RedisClusterProvider(config.getCluster(), redisClient);
    }

    @Bean
    @ConditionalOnMissingBean(AtomicIOStateProvider.class)
    public AtomicIOStateProvider redisStateProvider(RedisClient redisClient) {
        log.info("AtomicIO: 启用 Redis State Provider (集群模式)");
        return new RedisStateProvider(redisClient);
    }
}
