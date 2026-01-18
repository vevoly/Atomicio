package io.github.vevoly.atomicio.core.handler;

import com.lmax.disruptor.EventHandler;
import io.github.vevoly.atomicio.core.message.RawBytesMessage;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.manager.DisruptorEntry;
import io.github.vevoly.atomicio.server.api.manager.GroupManager;
import io.github.vevoly.atomicio.server.api.manager.IOEventManager;
import io.github.vevoly.atomicio.server.api.manager.SessionManager;
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

    private final IOEventManager eventManager;
    private final SessionManager sessionManager;
    private final GroupManager groupManager;

    public DisruptorEventHandler(AtomicIOEngine engine) {
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
        // 创建一个特殊的、直接持有最终字节的消息
        AtomicIOMessage forwardedMessage = new RawBytesMessage(message.getPayload());
        switch (message.getMessageType()) {
            case SEND_TO_USER:
                sessionManager.sendToUserLocally(message.getTarget(), forwardedMessage);
                break;
            case SEND_TO_GROUP:
                groupManager.sendToGroupLocally(message.getTarget(), forwardedMessage,
                        message.getExcludeUserIds() != null ? message.getExcludeUserIds().toArray(new String[0]) : null);
                break;
            case BROADCAST:
                sessionManager.broadcastLocally(forwardedMessage);
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

