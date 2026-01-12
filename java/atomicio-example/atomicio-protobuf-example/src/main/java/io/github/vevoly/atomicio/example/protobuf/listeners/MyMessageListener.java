package io.github.vevoly.atomicio.example.protobuf.listeners;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.server.api.listeners.MessageEventListener;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufMessage;
import io.github.vevoly.atomicio.example.protobuf.cmd.ProtobufExampleCmd;
import io.github.vevoly.atomicio.example.protobuf.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * protobuf 消息监听器
 *
 * @since 0.4.2
 * @author vevoly
 */
@Slf4j
@Component
public class MyMessageListener implements MessageEventListener {

    @Override
    public void onMessage(AtomicIOSession session, AtomicIOMessage message) {
        int commandId = message.getCommandId();
        byte[] payload = message.getPayload();

        try {
            switch (commandId) {
                case AtomicIOCommand.HEARTBEAT:
                    log.info("收到客户端 {} 心跳.", Optional.ofNullable(session.getAttribute(AtomicIOSessionAttributes.USER_ID)));
                    break;
                case ProtobufExampleCmd.LOGIN:
                    // 将 payload 字节解码成我们自己的业务消息
                    LoginRequest loginRequest = LoginRequest.parseFrom(payload);
                    handleLogin(session, loginRequest);
                    break;
                 case ProtobufExampleCmd.P2P_MESSAGE:
                     P2PMessageRequest p2pMessage = P2PMessageRequest.parseFrom(payload);
                     handleP2PMessage(session, p2pMessage);
                     break;
                default:
                    log.warn("收到未知命令: {}", commandId);
            }
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse protobuf payload for commandId: {}", commandId, e);
            session.close();
        }
    }

    /**
     * 处理登录逻辑
     * @param session   消息发送者的会话
     * @param request   已经解码的登录请求
     */
    private void handleLogin(AtomicIOSession session, LoginRequest request) {
        String userId = request.getUserId();
        String token = request.getToken();
        log.info("Handling login for user: {}", userId);

        // 模拟认证
        if (token != null && !token.isEmpty()) {
            // 通过 session.getEngine() 调用引擎服务
            session.getEngine().bindUser(AtomicIOBindRequest.of(userId), session);
            log.info("User {} authenticated and bound to session {}", userId, session.getId());
            // 回复客户端
            AtomicIOMessage response = ProtobufMessage.of(ProtobufExampleCmd.LOGIN_RESPONSE,
                    LoginResponse.newBuilder().setSuccess(true).setServerTime(System.currentTimeMillis()).build());
            session.send(response);
        } else {
            session.close();
        }
    }

    /**
     * 处理点对点消息逻辑
     * @param session 消息发送者会话
     * @param request 已经解码的 P2P 消息请求
     */
    private void handleP2PMessage(AtomicIOSession session, P2PMessageRequest request) {

        // 1. 获取发送者和接收者的信息
        String fromUserId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        String toUserId = request.getToUserId();
        String content = request.getContent();
        String clientMessageId = request.getClientMessageId();
        // 2. 安全检查
        if (fromUserId == null) {
            log.warn("An unauthenticated session {} tried to send a P2P message.", session.getId());
            session.close();
            return;
        }
        if (toUserId == null || toUserId.isEmpty() || clientMessageId == null || clientMessageId.isEmpty()) {
            log.warn("Invalid P2P request from user {}: missing toUserId or clientMessageId.", fromUserId);
            // 请求无效，也给一个失败的 ACK
            P2PMessageAck invalidAck = P2PMessageAck.newBuilder()
                    .setClientMessageId(clientMessageId != null ? clientMessageId : "")
                    .setSuccess(false)
                    .setCode(400)
                    .setMessage("Missing recipient or client message id")
                    .build();
            session.send(ProtobufMessage.of(ProtobufExampleCmd.P2P_MESSAGE_ACK, invalidAck));
            return;
        }
        log.info("User '{}' sends message (clientMsgId:{}) to user '{}'", fromUserId, clientMessageId, toUserId);
        long serverMessageId = System.nanoTime(); // 用纳秒时间戳模拟唯一ID
        // 4. 构建需要转发给接收者的“通知”消息
        P2PMessageNotify notifyMessageBody = P2PMessageNotify.newBuilder()
                .setFromUserId(fromUserId)
                .setContent(content)
                .setServerMessageId(serverMessageId)
                .setServerTime(System.currentTimeMillis())
                .build();
        AtomicIOMessage messageToForward = ProtobufMessage.of(ProtobufExampleCmd.P2P_MESSAGE_NOTIFY, notifyMessageBody);
        // 5. 调用引擎的 sendToUser 方法进行消息路由和投递
        session.getEngine().sendToUser(toUserId, messageToForward);
        // 6. 给发送者一个发送成功的回执 (ACK)
        P2PMessageAck successAck = P2PMessageAck.newBuilder()
                .setClientMessageId(clientMessageId)
                .setSuccess(true)
                .setServerMessageId(serverMessageId)
                .setTimestamp(System.currentTimeMillis())
                .build();
        session.send(ProtobufMessage.of(ProtobufExampleCmd.P2P_MESSAGE_ACK, successAck));
    }

}
