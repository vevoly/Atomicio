package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.core.manager.AtomicIOEngineLifecycleManager;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.listeners.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * 引擎生命周期管理器自动装配
 *
 * @since 0.6.4
 * @author vevoly
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AtomicIOEngine.class)
public class AtomicIOEngineLifecycleAutoConfiguration {

    /**
     * 创建生命周期管理器 Bean
     * @param engine
     * @return
     */
    @Bean
    @ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
    public SmartLifecycle atomicIOEngineLifecycleManager(
            AtomicIOEngine engine,
            ObjectProvider<List<ConnectionRejectListener>> connectionRejectListenersProvider,
            ObjectProvider<List<EngineReadyListener>> engineReadyListenersProvider,
            ObjectProvider<List<ConnectEventListener>> connectEventListenersProvider,
            ObjectProvider<List<DisconnectEventListener>> disconnectEventListenerProvider,
            ObjectProvider<List<MessageEventListener>> messageEventListenersProvider,
            ObjectProvider<List<ErrorEventListener>> errorEventListenersProvider,
            ObjectProvider<List<IdleEventListener>> idleEventListenersProvider,
            ObjectProvider<List<SessionReplacedListener>> sessionReplacedListenersProvider
    ) {
        if (!(engine instanceof DefaultAtomicIOEngine)) {
            return new NoOpSmartLifecycle(); // 返回一个空实现
        }
        // 将所有依赖都传递给 LifecycleManager
        return new AtomicIOEngineLifecycleManager(
                (DefaultAtomicIOEngine) engine,
                connectionRejectListenersProvider.getIfAvailable(Collections::emptyList),
                engineReadyListenersProvider.getIfAvailable(Collections::emptyList),
                connectEventListenersProvider.getIfAvailable(Collections::emptyList),
                disconnectEventListenerProvider.getIfAvailable(Collections::emptyList),
                messageEventListenersProvider.getIfAvailable(Collections::emptyList),
                errorEventListenersProvider.getIfAvailable(Collections::emptyList),
                idleEventListenersProvider.getIfAvailable(Collections::emptyList),
                sessionReplacedListenersProvider.getIfAvailable(Collections::emptyList)
        );
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
