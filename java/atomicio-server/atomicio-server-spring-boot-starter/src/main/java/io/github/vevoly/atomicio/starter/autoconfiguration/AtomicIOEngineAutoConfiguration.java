package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.core.handler.AtomicIOCommandDispatcher;
import io.github.vevoly.atomicio.core.listener.WelcomeBannerPrinter;
import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.auth.AtomicIOAuthenticator;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.api.manager.*;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

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
        "io.github.vevoly.atomicio.starter.autoconfiguration.AtomicIOClusterCoreAutoConfiguration",
})
public class AtomicIOEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
    public AtomicIOEngine atomicIOEngine(
            AtomicIOProperties config,
            DisruptorManager disruptorManager,
            IOEventManager eventManager,
            SessionManager sessionManager,
            GroupManager groupManager,
            AtomicIOServerCodecProvider codecProvider,
            AtomicIOStateProvider stateProvider,
            StateManager stateManager,
            ObjectProvider<AtomicIOClusterProvider> clusterProvider,
            ObjectProvider<ClusterManager> clusterManager,
            @Lazy AtomicIOCommandDispatcher commandDispatcher
    ) {
        log.info("AtomicIO: 创建 AtomicIOEngine");
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
                clusterManager.getIfAvailable(),
                commandDispatcher
        );
    }

    /**
     * 创建框架核心指令分发器 Handler。
     * <p>
     * 这个 Handler 依赖于 AtomicIOEngine 和一个由用户提供的 Authenticator Bean。
     *
     * @param engine        自动注入的引擎实例。
     * @param authenticator 由用户应用程序提供的认证器实例。如果用户没有提供，容器将启动失败，
     *                      这是一个“快速失败”的设计，强制用户完成必要的安全配置。
     * @return a {@link AtomicIOCommandDispatcher} instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public AtomicIOCommandDispatcher atomicIOCommandDispatcher(AtomicIOEngine engine, AtomicIOPayloadParser payloadParser, AtomicIOAuthenticator authenticator) {
        log.info("Creating FrameworkCommandDispatcher, powered by user-provided [{}].", authenticator.getClass().getSimpleName());
        return new AtomicIOCommandDispatcher(engine, payloadParser, authenticator);
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
