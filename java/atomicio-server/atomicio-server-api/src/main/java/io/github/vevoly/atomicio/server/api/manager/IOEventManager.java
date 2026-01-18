package io.github.vevoly.atomicio.server.api.manager;

import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.constants.ConnectionRejectType;
import io.github.vevoly.atomicio.server.api.constants.IdleState;
import io.github.vevoly.atomicio.server.api.listeners.*;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.netty.channel.Channel;

/**
 * IO 事件管理器接口
 *
 * @since 0.6.5
 * @author vevoly
 */
public interface IOEventManager {


    // 注册监听器接口
    void onConnectionReject(ConnectionRejectListener listener);
    void onReady(EngineReadyListener listener);
    void onConnect(ConnectEventListener listener);
    void onDisconnect(DisconnectEventListener listener);
    void onMessage(MessageEventListener listener);
    void onError(ErrorEventListener listener);
    void onIdle(IdleEventListener listener);
    void onSessionReplaced(SessionReplacedListener listener);

    // 事件分发接口
    void fireConnectionRejectEvent(Channel channel, ConnectionRejectType rejectType, Throwable cause);
    void fireEngineReadyEvent(AtomicIOEngine engine);
    void fireConnectEvent(AtomicIOSession session);
    void fireDisconnectEvent(AtomicIOSession session);
    void fireMessageEvent(AtomicIOSession session, AtomicIOMessage message);
    void fireErrorEvent(AtomicIOSession session, Throwable cause);
    void fireIdleEvent(AtomicIOSession session, IdleState state);
    void fireSessionReplacedEvent(AtomicIOSession oldSession, AtomicIOSession newSession);
}
