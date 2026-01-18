package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.server.api.constants.IdleState;
import io.github.vevoly.atomicio.server.api.listeners.IdleEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MyIdleListener implements IdleEventListener {

    @Override
    public void onIdle(AtomicIOSession session, IdleState state) {
        if (state == IdleState.READER_IDLE) {
            String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
            log.warn("Reader idle timeout for session {} (user: {}). Closing connection.",
                    session.getId(), userId != null ? userId : "N/A");
            // 关闭僵尸连接
            session.close();
        }
    }
}
