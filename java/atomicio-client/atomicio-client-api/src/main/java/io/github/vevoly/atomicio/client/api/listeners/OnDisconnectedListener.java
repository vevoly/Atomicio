package io.github.vevoly.atomicio.client.api.listeners;

import io.github.vevoly.atomicio.client.api.AtomicIOClient;

/**
 * 客户端断开连接监听器
 *
 * @since 0.5.0
 * @author vevoly
 */
@FunctionalInterface
public interface OnDisconnectedListener {
    void onDisconnected(AtomicIOClient client);
}
