package io.github.vevoly.atomicio.example.protobuf.server.listeners;

import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGenerator;
import io.github.vevoly.atomicio.example.protobuf.common.cmd.BusinessCommand;
import io.github.vevoly.atomicio.example.protobuf.proto.*;
import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.listeners.MessageEventListener;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * protobuf 消息监听器
 *
 * @since 0.4.2
 * @author vevoly
 */
@Slf4j
@Component
@AllArgsConstructor
public class MyMessageListener implements MessageEventListener {

    private final AtomicIOIdGenerator idGenerator;

    private final AtomicIOPayloadParser payloadParser;

    @Override
    public void onMessage(AtomicIOSession session, AtomicIOMessage message) {
        log.debug("收到消息 from user '{}': commandId={}", session.getUserId(), message.getCommandId());

        try {
            switch (message.getCommandId()) {
                case BusinessCommand.P2P_MESSAGE_REQUEST:
                    handleP2PMessage(session, payloadParser.parse(message, P2PMessageRequest.class));
                    break;

                case BusinessCommand.GROUP_MESSAGE_REQUEST:
                    handleGroupMessage(session, payloadParser.parse(message, GroupMessageRequest.class));
                    break;

                default:
                    log.warn("收到未知命令 from user '{}': {}", session.getUserId(), message.getCommandId());
            }
        } catch (Exception e) {
            log.error("解析或处理消息失败 for commandId: {}", message.getCommandId(), e);
            session.close();
        }
    }

    /**
     * 处理点对点消息
     */
    private void handleP2PMessage(AtomicIOSession session, P2PMessageRequest request) {
        String fromUserId = session.getUserId();
        String toUserId = request.getToUserId();
        String clientMessageId = request.getClientMessageId();

        // 1. 构建并发送 ACK
        long serverMessageId = idGenerator.nextId();
        P2PMessageAck ackPayload = P2PMessageAck.newBuilder()
                .setClientMessageId(clientMessageId)
                .setSuccess(true)
                .setServerMessageId(serverMessageId)
                .setTimestamp(System.currentTimeMillis())
                .build();
        AtomicIOMessage ackMessage = session.getEngine().getCodecProvider()
                .createResponse(null, BusinessCommand.P2P_MESSAGE_ACK, ackPayload);
        session.send(ackMessage);

        log.info("P2P message from '{}' to '{}' (clientMsgId:{}) acknowledged with serverMsgId:{}.",
                fromUserId, toUserId, clientMessageId, serverMessageId);

        // 2. 构建并转发 Notify
        P2PMessageNotify notifyPayload = P2PMessageNotify.newBuilder()
                .setFromUserId(fromUserId)
                .setContent(request.getContent())
                .setServerMessageId(serverMessageId)
                .setServerTime(System.currentTimeMillis())
                .build();
        AtomicIOMessage notifyMessage = session.getEngine().getCodecProvider()
                .createResponse(null, BusinessCommand.P2P_MESSAGE_NOTIFY, notifyPayload);

        session.getEngine().sendToUser(toUserId, notifyMessage);
    }

    /**
     * 处理群消息
     */
    private void handleGroupMessage(AtomicIOSession session, GroupMessageRequest request) {
        String fromUserId = session.getUserId();
        String groupId = request.getGroupId();
        String clientMessageId = request.getClientMessageId();

        // 1. (可选) 发送 ACK 给发送者
        long serverMessageId = idGenerator.nextId();
        GroupMessageAck ackPayload = GroupMessageAck.newBuilder()
                .setClientMessageId(clientMessageId)
                .setSuccess(true)
                .setServerMessageId(serverMessageId)
                .build();
        AtomicIOMessage ackMessage = session.getEngine().getCodecProvider()
                .createResponse(null, BusinessCommand.GROUP_MESSAGE_ACK, ackPayload);
        session.send(ackMessage);

        log.info("Group message from '{}' to group '{}' (clientMsgId:{}) acknowledged with serverMsgId:{}.",
                fromUserId, groupId, clientMessageId, serverMessageId);

        // 2. 构建并广播 Notify 给群成员 (排除发送者)
        GroupMessageNotify notifyPayload = GroupMessageNotify.newBuilder()
                .setGroupId(groupId)
                .setFromUserId(fromUserId)
                .setContent(request.getContent())
                .setServerMessageId(serverMessageId)
                .setServerTime(System.currentTimeMillis())
                .build();
        AtomicIOMessage notifyMessage = session.getEngine().getCodecProvider()
                .createResponse(null, BusinessCommand.GROUP_MESSAGE_NOTIFY, notifyPayload);

        session.getEngine().sendToGroup(groupId, notifyMessage, Collections.singleton(fromUserId));
    }
}
