package io.github.vevoly.atomicio.core.engine;

import com.lmax.disruptor.EventHandler;
import io.github.vevoly.atomicio.core.event.AtomicIOEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Disruptor 事件的最终消费者。
 * 这个类的 onEvent 方法会在独立的业务线程中被调用。
 *
 * @since 0.0.3
 * @author vevoly
 */
@Slf4j
@AllArgsConstructor
public class AtomicIOEventHandler implements EventHandler<AtomicIOEvent> {

    private final DefaultAtomicIOEngine engine;

    @Override
    public void onEvent(AtomicIOEvent atomicIOEvent, long sequence, boolean endOfBatch) throws Exception {
        try {
            switch (atomicIOEvent.getType()) {
                case CONNECT:
                    engine.fireSessionEvent(atomicIOEvent.getType(), atomicIOEvent.getSession());
                    break;
                case DISCONNECT:
                    String userId = atomicIOEvent.getSession().getAttribute("userId");
                    if (userId != null) {
                        engine.unbindUserInternal(userId, atomicIOEvent.getSession());
                    }
                    engine.fireSessionEvent(atomicIOEvent.getType(), atomicIOEvent.getSession());
                    break;
                case IDLE:
                    engine.fireSessionEvent(atomicIOEvent.getType(), atomicIOEvent.getSession());
                    break;
                case MESSAGE:
                    engine.fireMessageEvent(atomicIOEvent.getSession(), atomicIOEvent.getMessage());
                    break;
                case ERROR:
                    engine.fireErrorEvent(atomicIOEvent.getSession(), atomicIOEvent.getCause());
                    break;
                default:
                    log.warn("Unhandled event type: {}", atomicIOEvent.getType());
                    break;
            }
        } finally {
            // 事件处理完成后，清理 Event 对象，以便 Disruptor 复用
            atomicIOEvent.clear();
        }
    }
}
