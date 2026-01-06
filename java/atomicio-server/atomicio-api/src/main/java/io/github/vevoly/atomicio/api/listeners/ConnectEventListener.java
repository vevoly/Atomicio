package io.github.vevoly.atomicio.api.listeners;

import io.github.vevoly.atomicio.api.AtomicIOSession;

/**
 * 连接事件监听器接口
 *
 * @since 0.1.1
 * @author vevoly
 */
@FunctionalInterface
public interface ConnectEventListener {

    /**
     * 当收到连接时被调用
     * @param session   当前会话
     */
    void onConnected(AtomicIOSession session);
}
