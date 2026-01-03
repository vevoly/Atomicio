package io.github.vevoly.atomicio.api.listeners;

import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.github.vevoly.atomicio.api.AtomicIOSession;

/**
 * 消息事件监听器
 *
 * @since 0.0.1
 * @author vevoly
 */
@FunctionalInterface
public interface MessageEventListener {

    /**
     * 当收到消息时被调用。
     * @param session 来源会话
     * @param message 收到的消息
     */
    void onMessage(AtomicIOSession session, AtomicIOMessage message);
}
