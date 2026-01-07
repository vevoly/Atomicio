package io.github.vevoly.atomicio.client.api.listeners;

import io.github.vevoly.atomicio.client.api.AtomicIOClient;

/**
 * 客户端连接监听器
 *
 * @since 0.5.0
 * @author vevoly
 */
@FunctionalInterface
public interface OnConnectedListener {
    void onConnected(AtomicIOClient client);
}

