package io.github.vevoly.atomicio.example.protobuf.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
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
        log.info("onKickOut: oldSession={}, newSession={}", oldSession.getId(), newSession.getId());
    }
}
