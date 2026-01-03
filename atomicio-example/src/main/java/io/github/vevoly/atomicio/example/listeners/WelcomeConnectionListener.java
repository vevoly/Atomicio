package io.github.vevoly.atomicio.example.listeners;

import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.listeners.ConnectEventListener;
import io.github.vevoly.atomicio.api.listeners.SessionEventListener;
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
