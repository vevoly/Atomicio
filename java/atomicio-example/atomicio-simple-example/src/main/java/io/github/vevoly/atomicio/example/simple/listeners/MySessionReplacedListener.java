package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.listeners.SessionReplacedListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 被踢下线监听器
 *
 * @since 0.5.2
 * @author vevoly
 */
@Slf4j
@Component
public class MySessionReplacedListener implements SessionReplacedListener {

    @Override
    public void onSessionReplaced(AtomicIOSession oldSession, AtomicIOSession newSession) {
        log.info("会话被挤下线: oldSession={}, newSession={}", oldSession.getId(), newSession.getId());
    }
}
