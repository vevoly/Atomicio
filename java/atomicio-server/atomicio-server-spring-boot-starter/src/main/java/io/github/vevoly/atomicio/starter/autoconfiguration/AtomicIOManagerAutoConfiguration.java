package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.core.manager.AtomicIOEventManager;
import io.github.vevoly.atomicio.core.manager.AtomicIOGroupManager;
import io.github.vevoly.atomicio.core.manager.AtomicIOSessionManager;
import io.github.vevoly.atomicio.core.manager.DefaultDisruptorManager;
import io.github.vevoly.atomicio.server.api.manager.DisruptorManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class AtomicIOManagerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DisruptorManager disruptorManager() {
        return new DefaultDisruptorManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public AtomicIOEventManager eventManager() {
        return new AtomicIOEventManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public AtomicIOSessionManager sessionManager() {
        return new AtomicIOSessionManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public AtomicIOGroupManager groupManager() {
        return new AtomicIOGroupManager();
    }

}
