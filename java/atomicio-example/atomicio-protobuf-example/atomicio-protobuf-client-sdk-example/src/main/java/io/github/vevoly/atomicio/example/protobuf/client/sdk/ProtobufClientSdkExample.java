package io.github.vevoly.atomicio.example.protobuf.client.sdk;

import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.codec.protobuf.ProtobufClientCodecProvider;
import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
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

    private static String currentUserId;
    private static String currentDeviceId;

    public static void main(String[] args) {

        // 手动创建和配置所有组件
        log.info("--- 手动 SDK 设置 ---");

        AtomicIOClientConfig config = new AtomicIOClientConfig();
        config.setServerHost("127.0.0.1");
        config.setServerPort(8308);
        config.getSsl().setTrustCertFromResource("server.crt"); // 信任服务端证书
        config.setMaxFrameLength(256);

        AtomicIOClientCodecProvider codecProvider = new ProtobufClientCodecProvider();

        // 手动实例化客户端核心实现
        AtomicIOClient client = new DefaultAtomicIOClient(config, codecProvider);

        // 注册事件监听器
        client.onConnected(v -> log.info(">>>>>>>>> SDK: 成功连接到服务器! <<<<<<<<<"));
        client.onDisconnected(v -> {
            log.warn(">>>>>>>>> SDK: 与服务器断开连接. <<<<<<<<<");
            System.exit(0); // 简单起见，断开就退出
        });
        client.onPushMessage(message -> handlePushMessage(message, codecProvider));
        client.onError(err -> log.error(">>>>>>>>> SDK: 发生错误 <<<<<<<<<", err));

        // 启动并执行业务流程
        try {
            log.info("SDK: 尝试连接...");
            client.connect().join();

            // 登录
            currentUserId = "user_" + ThreadLocalRandom.current().nextInt(1000, 9999);
            currentDeviceId = "sdk-device-" + currentUserId;

            client.login(currentUserId, "any-valid-token", currentDeviceId)
                    .thenCompose(loginResult -> {
                        if (loginResult.success()) {
                            log.info("<<<<<<<<< SDK: 登录成功! {}", loginResult.errorMessage());
                            // 登录成功后，加入群组
                            return client.joinGroup("public-group");
                        } else {
                            log.error("<<<<<<<<< SDK: 登录失败: {}", loginResult.errorMessage());
                            return CompletableFuture.failedFuture(new RuntimeException("Login failed"));
                        }
                    })
                    .thenAccept(v -> {
                        log.info("<<<<<<<<< SDK: 成功加入群组 'public-group'!");
                        // 所有准备工作完成，启动控制台输入
                        startConsoleInput(client, codecProvider);
                    })
                    .exceptionally(ex -> {
                        log.error("SDK: 启动流程失败", ex.getCause());
                        client.disconnect();
                        return null;
                    })
                    .join(); // 阻塞等待整个启动流程完成

        } catch (Exception e) {
            log.error("SDK: 启动或登录过程中发生严重错误", e);
            client.disconnect();
        }
    }

    /**
     * 处理服务器主动推送的消息
     */
    private static void handlePushMessage(AtomicIOMessage message, AtomicIOClientCodecProvider codecProvider) {
        try {
            switch (message.getCommandId()) {
                case BusinessCommand.P2P_MESSAGE_NOTIFY:
                    P2PMessageNotify p2pNotify = codecProvider.parsePayloadAs(message, P2PMessageNotify.class);
                    log.info("<<<<<<<<< [私聊] 收到来自 [{}]: {}", p2pNotify.getFromUserId(), p2pNotify.getContent());
                    break;
                case BusinessCommand.GROUP_MESSAGE_NOTIFY:
                    GroupMessageNotify groupNotify = codecProvider.parsePayloadAs(message, GroupMessageNotify.class);
                    log.info("<<<<<<<<< [群聊-{}] 收到来自 [{}]: {}", groupNotify.getGroupId(), groupNotify.getFromUserId(), groupNotify.getContent());
                    break;
                default:
                    log.warn("<<<<<<<<< 收到未处理的推送消息，指令ID: {}", message.getCommandId());
            }
        } catch (Exception e) {
            log.error("<<<<<<<<< 解析推送消息 payload 错误, 指令ID: {}", message.getCommandId(), e);
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

                    AtomicIOMessage msg = codecProvider.createRequest(0, BusinessCommand.P2P_MESSAGE_REQUEST, currentDeviceId, payload);
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

                    AtomicIOMessage msg = codecProvider.createRequest(0, BusinessCommand.GROUP_MESSAGE_REQUEST, currentDeviceId, payload);
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
