package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.core.manager.AtomicIOClusterManager;
import io.github.vevoly.atomicio.core.manager.AtomicIOStateManager;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.DisruptorManager;
import io.github.vevoly.atomicio.server.api.manager.StateManager;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集群管理自动装配
 *
 * @since 0.6.4
 * @author vevoly
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = {
        "io.github.vevoly.atomicio.starter.autoconfiguration.AtomicIOLocalStateFallbackAutoConfiguration",
        "io.github.vevoly.atomicio.starter.autoconfiguration.AtomicIOCodecAutoConfiguration"
})
public class AtomicIOClusterCoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(value = {
            AtomicIOClusterProvider.class,
            AtomicIOServerCodecProvider.class
    })
    @ConditionalOnMissingBean(ClusterManager.class)
    public ClusterManager clusterManager(
            AtomicIOProperties config,
            AtomicIOClusterProvider provider,
            AtomicIOServerCodecProvider codecProvider,
            DisruptorManager disruptor
    ) {
        log.info("AtomicIO: 检测到集群驱动 {}，启动通用集群管理器...", provider.getClass().getSimpleName());
        return new AtomicIOClusterManager(config, provider, codecProvider, disruptor);
    }

    @Bean
    @ConditionalOnBean(AtomicIOStateProvider.class)
    @ConditionalOnMissingBean(StateManager.class)
    public StateManager stateManager(AtomicIOStateProvider stateProvider, ObjectProvider<ClusterManager> clusterManagerProvider) {
        log.info("AtomicIO: 启用集群状态管理器 (集群模式)");
        return new AtomicIOStateManager(stateProvider, clusterManagerProvider.getIfAvailable());
    }
}
