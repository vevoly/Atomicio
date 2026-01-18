package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
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
                // 原样回显或心跳处理
                session.send(message);
                break;
        }
        session.send(message);
    }

    private void handleLogin(AtomicIOSession session, TextMessage message) {
        // 1. 协议解析 (由 Decoder 保证了 message.getDeviceId() 已经有值)
        // 此时 content = "userId:token"
        String[] parts = message.getContent().split(":", 2);
        if (parts.length != 2) {
            session.send(new TextMessage(BusinessCommand.LOGIN, message.getDeviceId(), "Error:Invalid content format"));
            return;
        }

        String userId = parts[0];
        String token = parts[1];
        String deviceId = message.getDeviceId();

        // 2. 第一步：在业务层进行身份认证（认证通过前，框架不感知该用户）
        if (!AuthService.verify(token)) {
            log.warn("用户认证失败: userId={}, token={}", userId, token);
            session.send(new TextMessage(BusinessCommand.LOGIN, deviceId, "Error:Unauthorized"));
            session.close(); // 认证失败立即关闭连接
            return;
        }

        // 3. 第二步：认证通过，构建注册请求通知框架
        // 注意：必须带上 deviceId 以便框架进行“多端登录”或“单点登录”决策
        AtomicIOBindRequest bindRequest = AtomicIOBindRequest.builder()
                .userId(userId)
                .deviceId(deviceId)
                .build();

        // 4. 第三步：调用框架核心 bindUser
        // 这里会触发：StateManager 更新 Provider -> 处理冲突(踢人) -> SessionManager 物理绑定
        session.getEngine().bindUser(bindRequest, session);

        // 5. 第四步：反馈给客户端
        log.info("用户 {} 认证成功并已绑定 Session, 设备: {}", userId, deviceId);
        session.send(new TextMessage(BusinessCommand.LOGIN, deviceId, "Success:Welcome"));
    }

    private void handleP2PMessage(AtomicIOSession session, TextMessage message) {
        // Content format: "toUserId:text"
        String[] parts = message.getContent().split(":", 2);
        if (parts.length != 2) return;

        String toUserId = parts[0];
        String msgBody = parts[1];

        // Retrieve internal attributes set during bindUser
        String fromUserId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        String deviceId = session.getAttribute(AtomicIOSessionAttributes.DEVICE_ID);

        // Forwarding message
        TextMessage forward = new TextMessage(
                BusinessCommand.P2P_MESSAGE,
                deviceId,
                fromUserId + ":" + msgBody
        );

        session.getEngine().sendToUser(toUserId, forward);
    }

    private void handleJoinGroup(AtomicIOSession session, TextMessage message) {
        // 协议: content = "groupId"
        String groupId = message.getContent();
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);

        if (userId != null && groupId != null && !groupId.isEmpty()) {
            System.out.println("用户 " + userId + " 尝试加入群组 " + groupId);
            // **调用引擎的 joinGroup 方法！**
            session.getEngine().joinGroup(groupId, userId);
            session.send(new TextMessage(BusinessCommand.JOIN_GROUP, null, "Success:Joined group " + groupId));
        }
    }

    private void handleKickOut(AtomicIOSession session, TextMessage message) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        String reason = message.getContent().split(":")[1];
        session.getEngine().kickUser(userId, new TextMessage(BusinessCommand.LOGOUT, null, reason));
    }

}
