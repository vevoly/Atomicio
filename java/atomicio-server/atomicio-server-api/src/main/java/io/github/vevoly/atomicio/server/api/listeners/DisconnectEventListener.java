package io.github.vevoly.atomicio.server.api.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;

/**
 * 断开事件接口
 *
 * @since 0.1.1
 * @author vevoly
 */
@FunctionalInterface
public interface DisconnectEventListener {

    /**
     * 当收到断开事件时，方法被调用
     * @param session
     */
    void onDisconnected(AtomicIOSession session);
}
