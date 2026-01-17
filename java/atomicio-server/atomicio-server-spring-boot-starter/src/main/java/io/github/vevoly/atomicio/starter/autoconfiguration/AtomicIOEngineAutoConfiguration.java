package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.core.listener.WelcomeBannerPrinter;
import io.github.vevoly.atomicio.core.manager.*;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.api.listeners.*;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.DisruptorManager;
import io.github.vevoly.atomicio.server.api.manager.StateManager;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 自动装配
 *
 * @since 0.0.6
 * @author vevoly
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AtomicIOProperties.class)
@AutoConfigureAfter(name = {
        "io.github.vevoly.atomicio.starter.autoconfiguration.AtomicIOManagerAutoConfiguration",
        "io.github.vevoly.atomicio.starter.autoconfiguration.AtomicIOLocalStateFallbackAutoConfiguration",
})
public class AtomicIOEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
    public AtomicIOEngine atomicIOEngine(
            AtomicIOProperties config,
            DisruptorManager disruptorManager,
            AtomicIOEventManager eventManager,
            AtomicIOSessionManager sessionManager,
            AtomicIOGroupManager groupManager,
            AtomicIOServerCodecProvider codecProvider,
            AtomicIOStateProvider stateProvider,
            StateManager stateManager,
            ObjectProvider<AtomicIOClusterProvider> clusterProvider,
            ObjectProvider<AtomicIOClusterManager> clusterManager
    ) {
        log.info("Createing DefaultAtomicIOEngine");
        return new DefaultAtomicIOEngine(
                config,
                disruptorManager,
                eventManager,
                sessionManager,
                groupManager,
                codecProvider,
                stateProvider,
                stateManager,
                clusterProvider.getIfAvailable(),
                clusterManager.getIfAvailable()
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

}
