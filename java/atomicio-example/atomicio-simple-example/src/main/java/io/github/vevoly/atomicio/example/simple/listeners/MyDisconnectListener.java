package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.listeners.DisconnectEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 断开事件监听器示例
 *
 * @since 0.1.1
 * @author vevoly
 */
@Slf4j
@Component
public class MyDisconnectListener implements DisconnectEventListener {
    @Override
    public void onDisconnected(AtomicIOSession session) {
        log.info("Disconnected from {}", session.getRemoteAddress());
    }
}
