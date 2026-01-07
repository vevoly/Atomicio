package io.github.vevoly.atomicio.client.api.listeners;

import io.github.vevoly.atomicio.api.AtomicIOMessage;

/**
 * 客户端消息监听器
 *
 * @since 0.5.0
 * @author vevoly
 */
@FunctionalInterface
public interface OnMessageListener {
    void onMessage(AtomicIOMessage message);
}
