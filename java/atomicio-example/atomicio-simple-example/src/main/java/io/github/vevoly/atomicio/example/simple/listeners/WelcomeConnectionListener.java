package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.listeners.ConnectEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 不同的连接监听器演示
 * 多个监听器监听一个事件
 *
 * @since 0.1.1
 * @author vevoly
 */
@Slf4j
@Component
public class WelcomeConnectionListener implements ConnectEventListener {

    @Override
    public void onConnected(AtomicIOSession session) {
        log.info("WELCOME: " + session.getRemoteAddress() + " connected");
    }
}
