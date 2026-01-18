package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.example.simple.cmd.BusinessCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.listeners.MessageEventListener;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户自定义消息监听器示例
 * sequenceId:commandId:deviceId:payload
 *
 * @since 0.0.7
 * @author vevoly
 */
@Slf4j
@Component
public class MyMessageListener implements MessageEventListener {

    @Override
    public void onMessage(AtomicIOSession session, AtomicIOMessage message) {
        // 进入此方法时，可以100%保证 session.isBound() == true
        log.info("收到来自用户 '{}' 的业务消息: {}", session.getUserId(), message);

        switch (message.getCommandId()) {
            case BusinessCommand.P2P_MESSAGE:
                handleP2PMessage(session, (TextMessage) message);
                break;
            case BusinessCommand.GROUP_MESSAGE:
                handleGroupMessage(session, (TextMessage) message);
                break;
            default:
                log.warn("收到未知的业务指令: {}", message.getCommandId());
                break;
        }
    }

    /**
     * 处理点对点消息。
     */
    private void handleP2PMessage(AtomicIOSession session, TextMessage message) {
        // 载荷协议: "toUserId:text_content"
        String[] parts = message.getContent().split(":", 2);
        if (parts.length != 2) {
            log.warn("无效的P2P消息格式: {}", message.getContent());
            return;
        }

        String toUserId = parts[0];
        String msgBody = parts[1];
        String fromUserId = session.getUserId();

        log.info("转发P2P消息: 从 '{}' -> 到 '{}', 内容: '{}'", fromUserId, toUserId, msgBody);

        // 创建要转发的消息。注意，我们构建了一个新的消息对象。
        // 转发的内容格式可以自定义，这里我们加上发送方ID
        TextMessage forwardMessage = new TextMessage(
                0, // 对于转发消息，sequenceId可以为0，因为发送方客户端不直接等待它的响应
                BusinessCommand.P2P_MESSAGE,
                session.getDeviceId(),
                fromUserId + ":" + msgBody
        );

        session.getEngine().sendToUser(toUserId, forwardMessage);
    }

    /**
     * 处理群消息。
     */
    private void handleGroupMessage(AtomicIOSession session, TextMessage message) {
        // 载荷协议: "groupId:text_content"
        String[] parts = message.getContent().split(":", 2);
        if (parts.length != 2) {
            log.warn("无效的群消息格式: {}", message.getContent());
            return;
        }

        String groupId = parts[0];
        String msgBody = parts[1];
        String fromUserId = session.getUserId();

        log.info("转发群消息: 从 '{}' -> 到群组 '{}', 内容: '{}'", fromUserId, groupId, msgBody);

        TextMessage forwardMessage = new TextMessage(
                0,
                BusinessCommand.GROUP_MESSAGE,
                session.getDeviceId(),
                fromUserId + ":" + msgBody
        );

        // 使用 engine 发送到群组，并排除发送者自己
        session.getEngine().sendToGroup(groupId, forwardMessage, fromUserId);
    }

}
