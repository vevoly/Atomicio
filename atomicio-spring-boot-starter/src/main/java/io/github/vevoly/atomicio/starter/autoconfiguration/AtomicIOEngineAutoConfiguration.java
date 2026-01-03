package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterType;
import io.github.vevoly.atomicio.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.api.listeners.*;
import io.github.vevoly.atomicio.core.cluster.RedisClusterProvider;
import io.github.vevoly.atomicio.core.engine.AtomicIOEngineLifecycleManager;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.core.listener.WelcomeBannerPrinter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@Configuration
@NoArgsConstructor
@EnableConfigurationProperties(AtomicIOProperties.class)
public class AtomicIOEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = AtomicIOConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
    public AtomicIOEngine atomicIOEngine(AtomicIOProperties properties) {
        AtomicIOClusterProvider clusterProvider = createClusterProvider(properties);
        return new DefaultAtomicIOEngine(properties, clusterProvider);
    }

    /**
     * 创建生命周期管理器 Bean
     * @param engine
     * @return
     */
    @Bean
    @ConditionalOnProperty(prefix = AtomicIOConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
    public SmartLifecycle atomicIOEngineLifecycleManager(
            AtomicIOEngine engine,
            ObjectProvider<List<EngineReadyListener>> engineReadyListenersProvider,
            ObjectProvider<List<ConnectEventListener>> connectEventListenersProvider,
            ObjectProvider<List<DisconnectEventListener>> disconnectEventListenerProvider,
            ObjectProvider<List<MessageEventListener>> messageEventListenersProvider,
            ObjectProvider<List<ErrorEventListener>> errorEventListenersProvider,
            ObjectProvider<List<IdleEventListener>> idleEventListenersProvider
    ) {
        if (!(engine instanceof DefaultAtomicIOEngine)) {
            return new NoOpSmartLifecycle(); // 返回一个空实现
        }
        // 将所有依赖都传递给 LifecycleManager
        return new AtomicIOEngineLifecycleManager(
                (DefaultAtomicIOEngine) engine,
                engineReadyListenersProvider.getIfAvailable(Collections::emptyList),
                connectEventListenersProvider.getIfAvailable(Collections::emptyList),
                disconnectEventListenerProvider.getIfAvailable(Collections::emptyList),
                messageEventListenersProvider.getIfAvailable(Collections::emptyList),
                errorEventListenersProvider.getIfAvailable(Collections::emptyList),
                idleEventListenersProvider.getIfAvailable(Collections::emptyList)
        );
    }

    /**
     * 内部 Bean 定义：创建默认的欢迎横幅打印机。
     * 这个 Bean 也是有条件创建的，只有在整个 AutoConfiguration 生效时才会被创建。
     */
    @Bean
    @ConditionalOnMissingBean(WelcomeBannerPrinter.class)
    public WelcomeBannerPrinter welcomeBannerPrinter() {
        return new WelcomeBannerPrinter();
    }

    private static AtomicIOClusterProvider createClusterProvider(AtomicIOProperties properties) {
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
