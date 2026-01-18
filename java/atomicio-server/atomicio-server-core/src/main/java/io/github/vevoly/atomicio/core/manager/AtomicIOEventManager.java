package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.manager.IOEventManager;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.constants.ConnectionRejectType;
import io.github.vevoly.atomicio.server.api.constants.IdleState;
import io.github.vevoly.atomicio.server.api.listeners.*;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * AtomicIO 事件管理器
 *
 * @since 0.5.8
 * @author vevoly
 */
@Slf4j
public class AtomicIOEventManager implements IOEventManager {

    // 存储监听器集合
    private final List<ConnectionRejectListener> connectionRejectListeners = new CopyOnWriteArrayList<>();
    private final List<EngineReadyListener> readyListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectEventListener> connectEventListeners = new CopyOnWriteArrayList<>();
    private final List<DisconnectEventListener> disconnectEventListeners = new CopyOnWriteArrayList<>();
    private final List<MessageEventListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<ErrorEventListener> errorListeners = new CopyOnWriteArrayList<>();
    private final List<IdleEventListener> idleEventListeners = new CopyOnWriteArrayList<>();
    private final List<SessionReplacedListener> sessionReplacedListeners = new CopyOnWriteArrayList<>();

    // 注册监听器
    @Override
    public void onConnectionReject(ConnectionRejectListener listener) {
        this.connectionRejectListeners.add(listener);
    }
    @Override
    public void onReady(EngineReadyListener listener) {
        this.readyListeners.add(listener);
    }
    @Override
    public void onConnect(ConnectEventListener listener) {
        this.connectEventListeners.add(listener);
    }
    @Override
    public void onDisconnect(DisconnectEventListener listener) {
        this.disconnectEventListeners.add(listener);
    }
    @Override
    public void onMessage(MessageEventListener listener) {
        this.messageListeners.add(listener);
    }
    @Override
    public void onError(ErrorEventListener listener) {
        this.errorListeners.add(listener);
    }
    @Override
    public void onIdle(IdleEventListener listener) {
        this.idleEventListeners.add(listener);
    }
    @Override
    public void onSessionReplaced(SessionReplacedListener listener) {
        this.sessionReplacedListeners.add(listener);
    }

    // 分发方法
    /**
     * 触发 连接拒绝 事件。
     */
    @Override
    public void fireConnectionRejectEvent(Channel channel, ConnectionRejectType rejectType, Throwable cause) {
        fireEvent(connectionRejectListeners, l -> l.onConnectionReject(channel, rejectType, cause));
    }

    /**
     * 触发 READY 事件
     */
    @Override
    public void fireEngineReadyEvent(AtomicIOEngine engine) {
        fireEvent(readyListeners, l -> l.onEngineReady(engine));
    }

    /**
     * 触发 CONNECT 事件
     */
    @Override
    public void fireConnectEvent(AtomicIOSession session) {
        fireEvent(connectEventListeners, l -> l.onConnected(session));
    }

    /**
     * 触发 DISCONNECT 事件
     */
    @Override
    public void fireDisconnectEvent(AtomicIOSession session) {
        fireEvent(disconnectEventListeners, l -> l.onDisconnected(session));
    }

    /**
     * 触发 MESSAGE 事件
     * @param session   当前会话
     * @param message   收到的消息
     */
    @Override
    public void fireMessageEvent(AtomicIOSession session, AtomicIOMessage message) {
        fireEvent(messageListeners, l -> l.onMessage(session, message));
    }

    /**
     * 触发 ERROR 事件
     * @param session   当前会话
     * @param cause     异常
     */
    @Override
    public void fireErrorEvent(AtomicIOSession session, Throwable cause) {
        fireEvent(errorListeners, l -> l.onError(session, cause));
    }

    /**
     * 触发 IDLE 事件
     * @param session
     * @param state
     */
    @Override
    public void fireIdleEvent(AtomicIOSession session, IdleState state) {
        fireEvent(idleEventListeners, l -> l.onIdle(session, state));
    }

    /**
     * 触发 Session 替换事件
     * @param oldSession
     * @param newSession
     */
    @Override
    public void fireSessionReplacedEvent(AtomicIOSession oldSession, AtomicIOSession newSession) {
        if (sessionReplacedListeners.isEmpty()) {
            log.warn("No SessionReplacedListeners registered. Closing old session {} by default.", oldSession.getId());
        } else {
            fireEvent(sessionReplacedListeners, l -> l.onSessionReplaced(oldSession, newSession));
        }
        if (oldSession.isActive()) {
            log.debug("Closing old session {} after firing sessionReplaced event.", oldSession.getId());
            oldSession.close();
        }
    }

    /**
     * 公用事件触发器
     * @param listeners 监听器列表
     * @param action    要执行的具体逻辑
     */
    private <L> void fireEvent(List<L> listeners, Consumer<L> action) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (L listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.error("Error executing listener: {}", listener.getClass().getSimpleName(), e);
            }
        }
    }
}
