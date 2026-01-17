package io.github.vevoly.atomicio.server.extension.redis.autoconfiguration;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 自动装配类
 *
 * @since 0.6.4
 * @author vevoly
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.lettuce.core.RedisClient")
@ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX_CLUSTER, name = "enabled", havingValue = "true")
public class AtomicIORedisBaseConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public io.lettuce.core.RedisClient redisClient(AtomicIOProperties config) {
        String uri = config.getCluster().getRedis().getUri();
        log.info("初始化共享 RedisClient， URI: {}", uri);
        return io.lettuce.core.RedisClient.create(uri);
    }
}
