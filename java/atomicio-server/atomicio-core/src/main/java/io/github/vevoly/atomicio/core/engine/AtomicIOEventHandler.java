package io.github.vevoly.atomicio.core.engine;

import com.lmax.disruptor.EventHandler;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessage;
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
            if (atomicIOEvent.getClusterMessage() != null) {
                handleClusterMessage(atomicIOEvent.getClusterMessage());
            } else {
                handleIOEvent(atomicIOEvent);
            }
        } finally {
            // 事件处理完成后，清理 Event 对象，以便 Disruptor 复用
            atomicIOEvent.clear();
        }
    }

    /**
     * 处理集群消息
     *
     * @param message   集群消息
     */
    private void handleClusterMessage(AtomicIOClusterMessage message) {
        switch (message.getMessageType()) {
            case SEND_TO_USER:
                engine.sendToUserLocally(message.getTarget(), message.getOriginalMessage());
                break;
            case SEND_TO_GROUP:
                engine.sendToGroupLocally(message.getTarget(), message.getOriginalMessage(),
                        message.getExcludeUserIds() != null ? message.getExcludeUserIds().toArray(new String[0]) : null);
                break;
            case BROADCAST:
                engine.broadcastLocally(message.getOriginalMessage());
                break;
            default:
                log.warn("Unhandled cluster message type: {}", message.getMessageType());
        }

    }

    /**
     * 处理 Netty IO
     * @param atomicIOEvent IO 事件
     */
    private void handleIOEvent(AtomicIOEvent atomicIOEvent) {
        switch (atomicIOEvent.getType()) {
            case READY:
                engine.fireEngineReadyEvent();
                break;
            case CONNECT:
                engine.fireConnectEvent(atomicIOEvent.getSession());
                break;
            case DISCONNECT:
                String userId = atomicIOEvent.getSession().getAttribute("userId");
                if (userId != null) {
                    engine.unbindUserInternal(userId, atomicIOEvent.getSession());
                }
                engine.fireDisconnectEvent(atomicIOEvent.getSession());
                break;
            case IDLE:
                engine.fireIdleEvent(atomicIOEvent.getSession(), atomicIOEvent.getIdleState());
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
    }

}

