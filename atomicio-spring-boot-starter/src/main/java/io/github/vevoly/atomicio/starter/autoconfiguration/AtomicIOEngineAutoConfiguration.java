package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterType;
import io.github.vevoly.atomicio.api.config.AtomicIOEngineConfig;
import io.github.vevoly.atomicio.api.listeners.ErrorEventListener;
import io.github.vevoly.atomicio.api.listeners.MessageEventListener;
import io.github.vevoly.atomicio.api.listeners.SessionEventListener;
import io.github.vevoly.atomicio.core.cluster.RedisClusterProvider;
import io.github.vevoly.atomicio.core.engine.AtomicIOEngineLifecycleManager;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.starter.config.AtomicIOProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * 自动装配
 *
 * @since 0.0.6
 * @author vevoly
 */
@Slf4j
@Configuration // 标记为 Spring 配置类
@NoArgsConstructor
@EnableConfigurationProperties(AtomicIOProperties.class)
public class AtomicIOEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AtomicIOEngine atomicIOEngine(AtomicIOProperties properties) {
        // 1. 创建 ClusterProvider
        AtomicIOClusterProvider clusterProvider = createClusterProvider(properties);
        // 2. 创建 EngineConfig
        AtomicIOEngineConfig config = new AtomicIOEngineConfig();
        config.setPort(properties.getPort());
        config.setBossThreads(properties.getBossThreads());
        config.setWorkerThreads(properties.getWorkerThreads());
        // 3. 创建 Engine 实例，注入依赖
        return new DefaultAtomicIOEngine(config, clusterProvider);
    }

    /**
     * 创建生命周期管理器 Bean
     * @param engine
     * @return
     */
    @Bean
    public SmartLifecycle atomicIOEngineLifecycleManager(
            AtomicIOEngine engine,
            ObjectProvider<List<SessionEventListener>> sessionListenersProvider,
            ObjectProvider<List<MessageEventListener>> messageEventListenersProvider,
            ObjectProvider<List<ErrorEventListener>> errorEventListenersProvider
    ) {
        if (!(engine instanceof DefaultAtomicIOEngine)) {
            return new NoOpSmartLifecycle(); // 返回一个空实现
        }
        // 将所有依赖都传递给 LifecycleManager
        return new AtomicIOEngineLifecycleManager(
                (DefaultAtomicIOEngine) engine,
                sessionListenersProvider.getIfAvailable(Collections::emptyList),
                messageEventListenersProvider.getIfAvailable(Collections::emptyList),
                errorEventListenersProvider.getIfAvailable(Collections::emptyList)
        );
    }

    private AtomicIOClusterProvider createClusterProvider(AtomicIOProperties properties) {
        AtomicIOProperties.Cluster clusterProps = properties.getCluster();
        if (!clusterProps.isEnabled()) {
            return null;
        }
        switch (AtomicIOClusterType.fromString(clusterProps.getType())) {
            case REDIS:
                return new RedisClusterProvider(clusterProps.getRedis().getUri());
            case ROCKETMQ:
                log.warn("RocketMQ cluster provider is not implemented yet.");
                return null;
            case KAFKA:
                log.warn("Kafka cluster provider is not implemented yet.");
                return null;
            default:
                throw new IllegalArgumentException("Unsupported cluster type: " + clusterProps.getType());
        }
    }

    private static class NoOpSmartLifecycle implements SmartLifecycle {
        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}
