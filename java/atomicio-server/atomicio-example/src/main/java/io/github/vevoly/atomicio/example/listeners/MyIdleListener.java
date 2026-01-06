package io.github.vevoly.atomicio.example.listeners;

import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.constants.IdleState;
import io.github.vevoly.atomicio.api.listeners.IdleEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MyIdleListener implements IdleEventListener {

    @Override
    public void onIdle(AtomicIOSession session, IdleState state) {
        log.info("Session {} from {} is idle, state: {}", session.getId(), session.getRemoteAddress(), state);
    }
}
