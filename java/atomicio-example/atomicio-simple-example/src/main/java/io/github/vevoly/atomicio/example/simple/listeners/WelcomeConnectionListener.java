package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.example.simple.cmd.BusinessCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.listeners.ConnectEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * 不同的连接监听器演示
 * 多个监听器监听一个事件
 *
 * @since 0.1.1
 * @author vevoly
 */
@Slf4j
@Component
public class WelcomeConnectionListener implements ConnectEventListener {

    @Override
    public void onConnected(AtomicIOSession session) {
        log.info("新用户连接: " + session.getRemoteAddress() + " 成功！");
        String welcomeMessage = buildWelcomeMenu();

        // 构建一个用于发送欢迎菜单的 TextMessage
        //    - sequenceId: 0 (服务器主动推送)
        //    - commandId: 一个自定义的系统通知ID
        //    - deviceId: null
        //    - payload: 帮助菜单字符串
        AtomicIOMessage menuMessage = new TextMessage(
                0,
                BusinessCommand.SYSTEM_WELCOME_NOTIFY,
                null,
                welcomeMessage
        );
        // 将消息发送给刚刚连接的客户端
        session.send(menuMessage);
    }

    /**
     * 构建帮助菜单字符串。
     * 使用 Markdown 格式可以使其在某些客户端中渲染得更漂亮。
     * @return 帮助菜单文本
     */
    private String buildWelcomeMenu() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================================================\n");
        sb.append("      Welcome to AtomicIO Text Protocol Example Server!      \n");
        sb.append("========================================================================\n");
        sb.append("COMMANDS (format: sequenceId:commandId:deviceId:payload):\n");
        sb.append("\n");
        sb.append("  --- Authentication ---\n");
        sb.append("  [Login]       1:101:my-pc:user1:any-token\n");
        sb.append("                (cmd=101, payload='userId:token')\n");
        sb.append("\n");
        sb.append("  --- Group Management ---\n");
        sb.append("  [Join Group]  2:201:my-pc:public-group\n");
        sb.append("                (cmd=201, payload='groupId')\n");
        sb.append("\n");
        sb.append("  --- Messaging (Framework Routing) ---\n");
        sb.append("  [Send P2P]    3:500:my-pc:user2|2001|Hello there!\n");
        sb.append("                (cmd=500, payload='toUserId|bizType|bizPayload')\n");
        sb.append("\n");
        sb.append("  [Send Group]  4:502:my-pc:public-group||3005|Hello group!\n");
        sb.append("                (cmd=502, payload='groupId|excludeIds|bizType|bizPayload')\n");
        sb.append("\n");
        sb.append("------------------------------------------------------------------------\n");
        return sb.toString();
    }
}
