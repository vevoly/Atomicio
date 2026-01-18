package io.github.vevoly.atomicio.server.api.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;

/**
 * 处理与 Session 生命周期相关的事件的监听器
 * (CONNECT, DISCONNECT, IDLE)
 *
 * @since 0.0.1
 * @author vevoly
 */
@FunctionalInterface
public interface SessionEventListener {

    /**
     * 事件发生时被调用
     * @param session 当前会话
     */
    void onSessionEvent(AtomicIOSession session);
}
