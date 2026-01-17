package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.core.manager.AtomicIOClusterManager;
import io.github.vevoly.atomicio.core.manager.AtomicIOStateManager;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.DisruptorManager;
import io.github.vevoly.atomicio.server.api.manager.StateManager;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
})
public class AtomicIOClusterCoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(AtomicIOClusterProvider.class)
    @ConditionalOnMissingBean(ClusterManager.class)
    public ClusterManager clusterManager(AtomicIOClusterProvider provider, DisruptorManager disruptor) {
        log.info("AtomicIO: 检测到集群驱动 {}，正在启动通用集群管理器...", provider.getClass().getSimpleName());
        return new AtomicIOClusterManager(provider, disruptor);
    }

    @Bean
    @ConditionalOnBean(ClusterManager.class)
    @ConditionalOnMissingBean(StateManager.class)
    public StateManager stateManager(AtomicIOStateProvider stateProvider, ObjectProvider<ClusterManager> clusterManagerProvider) {
        return new AtomicIOStateManager(stateProvider, clusterManagerProvider.getIfAvailable());
    }
}
