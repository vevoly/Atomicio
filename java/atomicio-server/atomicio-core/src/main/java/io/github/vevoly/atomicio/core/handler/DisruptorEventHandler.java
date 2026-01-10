package io.github.vevoly.atomicio.core.handler;

import com.lmax.disruptor.EventHandler;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.core.cluster.ReconstructedMessage;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.core.manager.AtomicIOEventManager;
import io.github.vevoly.atomicio.core.manager.AtomicIOGroupManager;
import io.github.vevoly.atomicio.core.manager.AtomicIOSessionManager;
import io.github.vevoly.atomicio.core.manager.DisruptorEntry;
import lombok.extern.slf4j.Slf4j;

/**
 * Disruptor 事件的最终消费者。
 * 这个类的 onEvent 方法会在独立的业务线程中被调用。
 *
 * @since 0.0.3
 * @author vevoly
 */
@Slf4j
public class DisruptorEventHandler implements EventHandler<DisruptorEntry> {

    private final AtomicIOEventManager eventManager;
    private final AtomicIOSessionManager sessionManager;
    private final AtomicIOGroupManager groupManager;

    public DisruptorEventHandler(DefaultAtomicIOEngine engine) {
        this.eventManager = engine.getEventManager();
        this.sessionManager = engine.getSessionManager();
        this.groupManager = engine.getGroupManager();
    }

    @Override
    public void onEvent(DisruptorEntry entry, long sequence, boolean endOfBatch) throws Exception {
        try {
            if (entry.getClusterMessage() != null) {
                handleClusterMessage(entry.getClusterMessage());
            } else if (entry.getType() != null) { // 确保是 IO 事件
                handleIOEvent(entry);
            }
        } finally {
            // 事件处理完成后，清理 Event 对象，以便 Disruptor 复用
            entry.clear();
        }
    }

    /**
     * 处理集群消息
     *
     * @param message   集群消息
     */
    private void handleClusterMessage(AtomicIOClusterMessage message) {
        AtomicIOMessage atomicIOMessage = new ReconstructedMessage(message.getCommandId(), message.getPayload());
        switch (message.getMessageType()) {
            case SEND_TO_USER:
                sessionManager.sendToUserLocally(message.getTarget(), atomicIOMessage);
                break;
            case SEND_TO_GROUP:
                groupManager.sendToGroupLocally(message.getTarget(), atomicIOMessage,
                        message.getExcludeUserIds() != null ? message.getExcludeUserIds().toArray(new String[0]) : null);
                break;
            case BROADCAST:
                sessionManager.broadcastLocally(atomicIOMessage);
                break;
            default:
                log.warn("Unhandled cluster message type: {}", message.getMessageType());
        }

    }

    /**
     * 处理 Netty IO
     * @param disruptorEntry IO 事件
     */
    private void handleIOEvent(DisruptorEntry disruptorEntry) {
        switch (disruptorEntry.getType()) {
            case CONNECT:
                eventManager.fireConnectEvent(disruptorEntry.getSession());
                break;
            case DISCONNECT:
                eventManager.fireDisconnectEvent(disruptorEntry.getSession());
                break;
            case IDLE:
                eventManager.fireIdleEvent(disruptorEntry.getSession(), disruptorEntry.getIdleState());
                break;
            case MESSAGE:
                eventManager.fireMessageEvent(disruptorEntry.getSession(), disruptorEntry.getMessage());
                break;
            case ERROR:
                eventManager.fireErrorEvent(disruptorEntry.getSession(), disruptorEntry.getCause());
                break;
            default:
                log.warn("Unhandled event type: {}", disruptorEntry.getType());
                break;
        }
    }

}

