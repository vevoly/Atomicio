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

import java.util.List;

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
            if (entry == null) return;
            if (entry.getClusterMessage() != null) {
                handleClusterMessage(entry.getClusterMessage());
            } else if (entry.getType() != null) { // 确保是 IO 事件
                handleIOEvent(entry);
            }
        } catch (Throwable throwable) {
            // 捕获 Throwable，防止 Disruptor 消费者线程崩溃退出
            log.error("Disruptor event handling failed at sequence {}", sequence, throwable);
        } finally {
            // 事件处理完成后，清理 Event 对象，以便 Disruptor 复用
            entry.clear();
        }
    }

    /**
     * 处理集群消息
     *
     * @param clusterMessage   集群消息
     */
    private void handleClusterMessage(AtomicIOClusterMessage clusterMessage) {
        // 创建一个特殊的、直接持有最终字节的消息
        AtomicIOMessage forwardedMessage = new RawBytesMessage(clusterMessage.getPayload());
        switch (clusterMessage.getMessageType()) {
            case SEND_TO_USER:
                sessionManager.sendToUserLocally(clusterMessage.getTargetUserId(), forwardedMessage);
                break;
            case SEND_TO_USERS_BATCH:
                handleBatchSend(clusterMessage, forwardedMessage);
                break;
            case SEND_TO_GROUP:
                groupManager.sendToGroupLocally(clusterMessage.getTargetGroupId(), forwardedMessage, clusterMessage.getExcludeUserIds());
                break;
            case BROADCAST:
                sessionManager.broadcastLocally(forwardedMessage);
                break;
            case KICK_OUT:
                handleKickOut(clusterMessage, forwardedMessage);
                break;
            default:
                log.warn("Unhandled cluster message type: {}", clusterMessage.getMessageType());
        }
    }

    /**
     * 踢人
     * @param clusterMessage
     * @param forwardedMessage
     */
    private void handleKickOut(AtomicIOClusterMessage clusterMessage, AtomicIOMessage forwardedMessage) {
        List<String> deviceIdsToKick = clusterMessage.getTargetDeviceIds();
        if (deviceIdsToKick == null || deviceIdsToKick.isEmpty()) {
            log.warn("Received KICK_OUT cluster message with no target deviceIds.");
            return;
        }

        AtomicIOMessage kickOutNotify = (clusterMessage.getPayload() != null)
                ? new RawBytesMessage(clusterMessage.getPayload())
                : null;

        // 遍历并调用按 deviceId 踢人方法
        for (String deviceId : deviceIdsToKick) {
            sessionManager.kickOutByDeviceId(deviceId, kickOutNotify);
        }
    }

    /**
     * 批量发送逻辑
     */
    private void handleBatchSend(AtomicIOClusterMessage clusterMessage, AtomicIOMessage message) {
        List<String> userIds = clusterMessage.getTargetUserIds();
        if (userIds == null || userIds.isEmpty()) return;

        // 批量场景下，建议对循环体进行异常隔离，防止某一个 Session 故障影响其他用户
        for (String userId : userIds) {
            try {
                sessionManager.sendToUserLocally(userId, message);
            } catch (Exception e) {
                log.error("Failed to send batch message to user: {}", userId, e);
            }
        }
    }

    /**
     * 处理 Netty IO
     * @param disruptorEntry IO 事件
     */
    private void handleIOEvent(DisruptorEntry disruptorEntry) {
        if (disruptorEntry == null) return;
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

