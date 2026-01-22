package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.core.manager.AtomicIOStateManager;
import io.github.vevoly.atomicio.core.state.AtomicIOLocalStateProvider;
import io.github.vevoly.atomicio.server.api.manager.StateManager;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地状态回退自动装配
 *
 * @since 0.6.4
 * @author vevoly
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class AtomicIOLocalStateFallbackAutoConfiguration {

    /**
     * Local State Provider (单机模式，默认回退)
     * 生效条件: 1. 容器中还没有 StateProvider Bean。
     * 这个条件保证了如果上面的 RedisStateConfiguration 生效了，这个就不会生效
     */
    @AutoConfigureAfter(name = "io.github.vevoly.atomicio.server.extension.redis.RedisProviderAutoConfiguration")
    @ConditionalOnMissingBean(AtomicIOStateProvider.class)
    static class LocalStateConfiguration {
        @Bean
        public AtomicIOStateProvider localStateProvider() {
            log.info("AtomicIO: 启用本地内存状态管理器 (单机模式)");
            return new AtomicIOLocalStateProvider();
        }

        @Bean
        @ConditionalOnBean(AtomicIOStateProvider.class)
        @ConditionalOnMissingBean(StateManager.class)
        public StateManager stateManager(AtomicIOStateProvider stateProvider) {
            return new AtomicIOStateManager(stateProvider, null);
        }
    }
}
