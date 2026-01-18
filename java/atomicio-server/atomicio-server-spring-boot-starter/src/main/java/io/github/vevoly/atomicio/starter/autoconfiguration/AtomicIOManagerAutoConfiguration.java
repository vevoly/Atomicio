package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.core.manager.AtomicIOEventManager;
import io.github.vevoly.atomicio.core.manager.AtomicIOGroupManager;
import io.github.vevoly.atomicio.core.manager.AtomicIOSessionManager;
import io.github.vevoly.atomicio.core.manager.DefaultDisruptorManager;
import io.github.vevoly.atomicio.server.api.manager.DisruptorManager;
import io.github.vevoly.atomicio.server.api.manager.GroupManager;
import io.github.vevoly.atomicio.server.api.manager.IOEventManager;
import io.github.vevoly.atomicio.server.api.manager.SessionManager;
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
    public IOEventManager eventManager() {
        return new AtomicIOEventManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager() {
        return new AtomicIOSessionManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public GroupManager groupManager(SessionManager sessionManager) {
        return new AtomicIOGroupManager(sessionManager);
    }

}
