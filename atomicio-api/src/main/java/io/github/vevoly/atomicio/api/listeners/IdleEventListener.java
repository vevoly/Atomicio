package io.github.vevoly.atomicio.api.listeners;

import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.constants.IdleState;

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
