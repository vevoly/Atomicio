package io.github.vevoly.atomicio.example.protobuf.client.sdk;

import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.codec.protobuf.ProtobufClientCodecProvider;
import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
import io.github.vevoly.atomicio.example.protobuf.client.sdk.handler.PushMessageHandler;
import io.github.vevoly.atomicio.example.protobuf.client.sdk.listeners.ConnectionListener;
import io.github.vevoly.atomicio.example.protobuf.common.cmd.BusinessCommand;
import io.github.vevoly.atomicio.example.protobuf.proto.GroupMessageNotify;
import io.github.vevoly.atomicio.example.protobuf.proto.GroupMessageRequest;
import io.github.vevoly.atomicio.example.protobuf.proto.P2PMessageNotify;
import io.github.vevoly.atomicio.example.protobuf.proto.P2PMessageRequest;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Protobuf Client SDK 示例
 *
 * @since 0.6.7
 * @author vevoly
 */
@Slf4j
public class ProtobufClientSdkExample {

    public static void main(String[] args) {

        // 手动创建和配置所有组件
        log.info("--- 手动 SDK 设置 ---");

        AtomicIOClientConfig config = new AtomicIOClientConfig();
        config.setServerHost("127.0.0.1");
        config.setServerPort(8308);
        config.getSsl().setTrustCertFromResource("server.crt"); // 信任服务端证书
        config.setMaxFrameLength(256);

        AtomicIOClientCodecProvider codecProvider = new ProtobufClientCodecProvider();
        AtomicIOClient client = new DefaultAtomicIOClient(config, codecProvider);

        // 实例化所有监听器
        ConnectionListener connectionListener = new ConnectionListener(client);
        PushMessageHandler pushMessageHandler = new PushMessageHandler(codecProvider);

        // 注册事件监听器
        client.onConnected(connectionListener::onConnected);
        client.onDisconnected(connectionListener::onDisconnected);
        client.onReconnecting(connectionListener::onReconnecting);
        client.onPushMessage(pushMessageHandler::handle);
        client.onError(err -> log.error(">>>>>>>>> 客户端捕获到未处理的严重异常! <<<<<<<<<", err));

        try {
            log.info("SDK: 尝试连接...");
            client.connect().join();
            startConsoleInput(client, codecProvider);
        } catch (Exception e) {
            log.error("SDK: 启动或登录过程中发生严重错误", e);
            client.disconnect();
        }
    }

    /**
     * 启动控制台输入线程
     */
    private static void startConsoleInput(AtomicIOClient client, AtomicIOClientCodecProvider codecProvider) {
        Thread consoleThread = new Thread(() -> {
            log.info("\n======================================================\n" +
                    "控制台已就绪. 输入指令:\n" +
                    "  @<userId>:<message>  - 发送私聊消息\n" +
                    "  #<groupId>:<message> - 发送群聊消息\n" +
                    "  exit                  - 退出\n" +
                    "======================================================");

            Scanner scanner = new Scanner(System.in);
            while (client.isConnected()) {
                String line = scanner.nextLine();
                if (line == null || "exit".equalsIgnoreCase(line)) {
                    client.disconnect();
                    break;
                }

                if (line.startsWith("@")) {
                    // 发送 P2P 消息
                    String[] parts = line.substring(1).split(":", 2);
                    if (parts.length != 2) continue;
                    String toUserId = parts[0].trim();
                    String content = parts[1].trim();

                    P2PMessageRequest payload = P2PMessageRequest.newBuilder()
                            .setClientMessageId(UUID.randomUUID().toString())
                            .setToUserId(toUserId)
                            .setContent(content)
                            .build();

                    AtomicIOMessage msg = codecProvider.createRequest(0, BusinessCommand.P2P_MESSAGE_REQUEST, "", payload);
                    client.sendToUsers(msg, toUserId);

                } else if (line.startsWith("#")) {
                    // 发送群消息
                    String[] parts = line.substring(1).split(":", 2);
                    if (parts.length != 2) continue;
                    String groupId = parts[0].trim();
                    String content = parts[1].trim();

                    GroupMessageRequest payload = GroupMessageRequest.newBuilder()
                            .setClientMessageId(UUID.randomUUID().toString())
                            .setGroupId(groupId)
                            .setContent(content)
                            .build();

                    AtomicIOMessage msg = codecProvider.createRequest(0, BusinessCommand.GROUP_MESSAGE_REQUEST, "", payload);
                    client.sendToGroup(msg, groupId, null);
                }
            }
            scanner.close();
            log.info("控制台输入线程已停止.");
        });
        consoleThread.setName("console-input-thread");
        consoleThread.start();
    }
}
