package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.api.listeners.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.List;

/**
 * AtomicIOEngine 的 Spring 生命周期管理器。
 * 这是一个适配器，将引擎的生命周期对接到 Spring 的 SmartLifecycle 体系中。
 * 将注册监听器和启动放在这里
 *
 * @since 0.0.7
 * @author vevoly
 */
@Slf4j
@AllArgsConstructor
public class AtomicIOEngineLifecycleManager implements SmartLifecycle {

    private final DefaultAtomicIOEngine engine;

    private final List<ConnectionRejectListener> connectionRejectListeners;
    private final List<EngineReadyListener> engineReadyListeners;
    private final List<ConnectEventListener> connectEventListeners;
    private final List<DisconnectEventListener> disconnectEventListeners;
    private final List<MessageEventListener> messageEventListeners;
    private final List<ErrorEventListener> errorEventListeners;
    private final List<IdleEventListener> idleEventListeners;
    private final List<SessionReplacedListener> sessionReplacedListeners;

    @Override
    public void start() {
        // 启动引擎之前，先完成所有注册
        registerListeners();
        try {
            engine.doStart();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start AtomicIOEngine", e);
        }
    }

    @Override
    public void stop() {
        if (!engine.isRunning()) {
            return;
        }
        engine.doStop();
    }

    @Override
    public boolean isRunning() {
        return engine.isRunning();
    }

    /**
     * 返回 true，告诉 Spring 容器要自动启动这个 Bean。
     */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * 用于异步关闭。
     * 因为我们的 doStop() 是同步的，所以我们直接调用它，然后立即执行回调。
     */
    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    /**
     * 定义生命周期的阶段。值越小，启动越早；值越大，关闭越早。
     * 这里返回一个较大的值 (Integer.MAX_VALUE)
     * - 启动时：让其他大多数 Bean (如数据库连接池) 先启动，我们的引擎最后启动。
     * - 关闭时：我们的引擎最先关闭 (停止接收新请求)，然后再关闭其他 Bean。
     * 这是一种非常安全的策略。
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * 注册事件监听器
     */
    private void registerListeners() {
        log.info("AtomicIO LifecycleManager: Auto-registering listeners...");
        connectionRejectListeners.forEach(engine::onSslHandshakeFailed);
        engineReadyListeners.forEach(engine::onReady);
        connectEventListeners.forEach(engine::onConnect);
        disconnectEventListeners.forEach(engine::onDisconnect);
        messageEventListeners.forEach(engine::onMessage);
        errorEventListeners.forEach(engine::onError);
        idleEventListeners.forEach(engine::onIdle);
        sessionReplacedListeners.forEach(engine::onSessionReplaced);
        log.info("已注册监听器: {} EngineReadyListener {} ConnectEventListener, {} DisconnectEventListener, {} MessageEventListener, \n" +
                        " {} ErrorEventListener, {} IdleEventListener, {} SessionReplacedListener, {} ConnectionRejectListener",
                engineReadyListeners.size(), connectEventListeners.size(), disconnectEventListeners.size(), messageEventListeners.size(),
                errorEventListeners.size(), idleEventListeners.size(), sessionReplacedListeners.size(), connectionRejectListeners.size());
    }

}
