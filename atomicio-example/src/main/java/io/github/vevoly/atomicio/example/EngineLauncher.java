package io.github.vevoly.atomicio.example;

import io.github.vevoly.atomicio.api.AtomicIOCommand;
import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.config.AtomicIOConfig;
import io.github.vevoly.atomicio.api.message.TextMessage;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;

public class EngineLauncher {

    private static AtomicIOEngine engine;

    public static void main(String[] args) {
        // 1. 创建引擎实例
        engine = new DefaultAtomicIOEngine(new AtomicIOConfig());
        // 2. 注册监听器
        engine.on(AtomicIOEventType.CONNECT, session -> {
            System.out.println("新连接建立: " + session.getId());
        });
        engine.on(AtomicIOEventType.DISCONNECT, session -> {
            System.out.println("连接断开: " + session.getId());
        });
        engine.onMessage(((session, message) -> {
            System.out.println("收到消息: " + new String(message.getPayload()));
            int commandId = message.getCommandId();
            // 使用 switch 语句进行命令分发
            switch (commandId) {
                case AtomicIOCommand.LOGIN:
                    handleLogin(session, (TextMessage) message);
                    break;
                case AtomicIOCommand.P2P_MESSAGE:
                    handleP2PMessage(session, (TextMessage) message);
                    break;
                case AtomicIOCommand.JOIN_GROUP:
                    handleJoinGroup(session, (TextMessage) message);
                    break;
                // 可以添加更多 case 来处理其他命令
                default:
                    System.err.println("收到未知指令: " + commandId);
                    break;
            }
            session.send(message);
        }));
        engine.onError(((session, cause) -> {
            System.err.println("会话 " + (session != null ? session.getId() : "N/A") + " 发生错误: " + cause.getMessage());
        }));

        // 3. 启动引擎
        System.out.println("Atomicio Engine 启动中...");
        engine.start();
        System.out.println("Atomicio Engine 启动完成!");
    }

    private static void handleLogin(AtomicIOSession session, TextMessage message) {
        // 协议: content = "userId:token"
        String content = message.getContent();
        String[] parts = content.split(":", 2);
        if (parts.length != 2) {
            session.send(new TextMessage(AtomicIOCommand.LOGIN, "Error:Invalid format. Use userId:token"));
            session.close();
            return;
        }

        String userId = parts[0];
        String token = parts[1];

        // 调用业务层的认证服务
        if (AuthService.verify(token)) {
            // **关键一步：认证成功后，调用引擎的 bindUser 方法！**
            engine.bindUser(userId, session);
            System.out.println("用户 " + userId + " 登录成功!");
            session.send(new TextMessage(AtomicIOCommand.LOGIN, "Success:Welcome " + userId));
        } else {
            System.out.println("用户 " + userId + " 登录失败!");
            session.send(new TextMessage(AtomicIOCommand.LOGIN, "Error:Invalid token"));
            session.close();
        }
    }

    private static void handleP2PMessage(AtomicIOSession session, TextMessage message) {
        // 协议: content = "toUserId:messageContent"
        String content = message.getContent();
        String[] parts = content.split(":", 2);
        if (parts.length != 2) { return; }

        String toUserId = parts[0];
        String messageContent = parts[1];
        String fromUserId = session.getAttribute("userId");

        System.out.println("用户 " + fromUserId + " 发送消息给 " + toUserId + ": " + messageContent);
        // **调用引擎的 sendToUser 方法！**
        TextMessage forwardMessage = new TextMessage(AtomicIOCommand.P2P_MESSAGE, fromUserId + ":" + messageContent);
        engine.sendToUser(toUserId, forwardMessage);
    }

    private static void handleJoinGroup(AtomicIOSession session, TextMessage message) {
        // 协议: content = "groupId"
        String groupId = message.getContent();
        String userId = session.getAttribute("userId");

        if (userId != null && groupId != null && !groupId.isEmpty()) {
            System.out.println("用户 " + userId + " 尝试加入群组 " + groupId);
            // **调用引擎的 joinGroup 方法！**
            engine.joinGroup(groupId, userId);
            session.send(new TextMessage(AtomicIOCommand.JOIN_GROUP, "Success:Joined group " + groupId));
        }
    }
}

/**
 * 模拟认证
 */
class AuthService {
    public static boolean verify(String token) {
        return token != null && !token.isEmpty();
    }
}
