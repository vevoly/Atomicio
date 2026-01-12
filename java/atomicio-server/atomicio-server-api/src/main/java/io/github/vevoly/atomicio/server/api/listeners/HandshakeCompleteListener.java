package io.github.vevoly.atomicio.server.api.listeners;

import io.github.vevoly.atomicio.server.api.AtomicIOSession;

/**
 * 握手监听器
 * 对于 WebSocket 或其他需要握手过程的协议。这个事件可以在协议握手成功后触发。
 *
 * @since 0.1.3
 * @author vevoly
 */
@FunctionalInterface
public interface HandshakeCompleteListener {

    void onHandshakeComplete(AtomicIOSession session);
}
