package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.server.api.listeners.MessageEventListener;
import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.example.simple.cmd.BusinessCommand;
import io.github.vevoly.atomicio.example.simple.service.AuthService;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户自定义消息监听器示例
 *
 * @since 0.0.7
 * @author vevoly
 */
@Slf4j
@Component
public class MyMessageListener implements MessageEventListener {

    @Override
    public void onMessage(AtomicIOSession session, AtomicIOMessage message) {
        log.info("收到消息: {}", message);
        int commandId = message.getCommandId();
        switch (commandId) {
            case BusinessCommand.LOGIN:
                handleLogin(session, (TextMessage) message);
                break;
            case BusinessCommand.P2P_MESSAGE:
                handleP2PMessage(session, (TextMessage) message);
                break;
            case BusinessCommand.JOIN_GROUP:
                handleJoinGroup(session, (TextMessage) message);
                break;
            case BusinessCommand.LOGOUT:
                handleKickOut(session, (TextMessage) message);
            default:
                log.info("收到未知指令: " + commandId);
                break;
        }
        session.send(message);
    }

    private void handleLogin(AtomicIOSession session, TextMessage message) {
        // 协议: content = "userId:token"
        String content = message.getContent();
        String[] parts = content.split(":", 2);
        if (parts.length != 2) {
            session.send(new TextMessage(BusinessCommand.LOGIN, "Error:Invalid format. Use userId:token"));
            session.close();
            return;
        }

        String userId = parts[0];
        String token = parts[1];

        // 调用业务层的认证服务
        if (AuthService.verify(token)) {
            // 认证成功后，调用引擎的 bindUser 方法
            session.getEngine().bindUser(AtomicIOBindRequest.of(userId), session);
            System.out.println("用户 " + userId + " 登录成功!");
            session.send(new TextMessage(BusinessCommand.LOGIN, "Success:Welcome " + userId));
        } else {
            System.out.println("用户 " + userId + " 登录失败!");
            session.send(new TextMessage(BusinessCommand.LOGIN, "Error:Invalid token"));
            session.close();
        }
    }

    private void handleP2PMessage(AtomicIOSession session, TextMessage message) {
        // 协议: content = "toUserId:messageContent"
        String content = message.getContent();
        String[] parts = content.split(":", 2);
        if (parts.length != 2) {
            return;
        }

        String toUserId = parts[0];
        String messageContent = parts[1];
        String fromUserId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);

        System.out.println("用户 " + fromUserId + " 发送消息给 " + toUserId + ": " + messageContent);
        // **调用引擎的 sendToUser 方法！**
        TextMessage forwardMessage = new TextMessage(BusinessCommand.P2P_MESSAGE, fromUserId + ":" + messageContent);
        session.getEngine().sendToUser(toUserId, forwardMessage);
    }

    private void handleJoinGroup(AtomicIOSession session, TextMessage message) {
        // 协议: content = "groupId"
        String groupId = message.getContent();
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);

        if (userId != null && groupId != null && !groupId.isEmpty()) {
            System.out.println("用户 " + userId + " 尝试加入群组 " + groupId);
            // **调用引擎的 joinGroup 方法！**
            session.getEngine().joinGroup(groupId, userId);
            session.send(new TextMessage(BusinessCommand.JOIN_GROUP, "Success:Joined group " + groupId));
        }
    }

    private void handleKickOut(AtomicIOSession session, TextMessage message) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        String reason = message.getContent().split(":")[1];
        session.getEngine().kickUser(userId, new TextMessage(BusinessCommand.LOGOUT, reason));
    }

}
