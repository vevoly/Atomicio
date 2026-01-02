package io.github.vevoly.atomicio.example.listeners;

import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.listeners.SessionEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户自定义监听器演示
 *
 * @since 0.0.7
 * @author vevoly
 */
@Slf4j
@Component
public class MyConnectionListener implements SessionEventListener {

    @Override
    public void onEvent(AtomicIOSession session) {
        if (session.isActive()) { // CONNECTED
            log.info("新连接建立: " + session.getId());
        } else { // DISCONNECTED
            log.info("连接断开: " + session.getId());
        }
    }

}
