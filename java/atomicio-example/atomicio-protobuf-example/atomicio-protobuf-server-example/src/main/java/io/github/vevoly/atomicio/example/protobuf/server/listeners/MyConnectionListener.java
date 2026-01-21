package io.github.vevoly.atomicio.example.protobuf.server.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.listeners.ConnectEventListener;
import io.github.vevoly.atomicio.server.api.listeners.DisconnectEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 连接监听器
 *
 * @since 0.4.2
 * @author vevoly
 */
@Slf4j
@Component
public class MyConnectionListener implements ConnectEventListener, DisconnectEventListener {

    @Override
    public void onConnected(AtomicIOSession session) {
        log.info("Client connected: {}", session.getRemoteAddress());
    }

    @Override
    public void onDisconnected(AtomicIOSession session) {
        log.info("Client disconnected: {}", session.getId());
    }
}
