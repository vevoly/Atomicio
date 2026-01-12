package io.github.vevoly.atomicio.server.api.listeners;

import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.constants.IdleState;

/**
 * 心跳事件监听缄口
 *
 * @since 0.1.2
 * @author vevoly
 */
@FunctionalInterface
public interface IdleEventListener {

    void onIdle(AtomicIOSession session, IdleState state);
}
