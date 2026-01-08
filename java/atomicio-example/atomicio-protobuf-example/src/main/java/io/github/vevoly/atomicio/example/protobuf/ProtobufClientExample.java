package io.github.vevoly.atomicio.example.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.vevoly.atomicio.api.AtomicIOCommand;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.github.vevoly.atomicio.api.codec.AtomicIOCodecProvider;
import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
import io.github.vevoly.atomicio.codec.ProtobufCodecProvider;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufMessage;
import io.github.vevoly.atomicio.example.protobuf.cmd.ProtobufExampleCmd;
import io.github.vevoly.atomicio.example.protobuf.proto.LoginRequest;
import io.github.vevoly.atomicio.example.protobuf.proto.LoginResponse;
import io.github.vevoly.atomicio.example.protobuf.proto.P2PMessageNotify;
import io.github.vevoly.atomicio.example.protobuf.proto.P2PMessageRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class ProtobufClientExample {

    public static void main(String[] args) {
        // 1. 配置客户端
        AtomicIOClientConfig config = new AtomicIOClientConfig(); // 使用默认配置
        // 2. 选择解码器
        AtomicIOCodecProvider codecProvider = new ProtobufCodecProvider();
        // 3.创建客户端实例
        AtomicIOClient client = new DefaultAtomicIOClient(config, codecProvider);
        // 4. 注册事件监听器
        client.onConnected(c -> {
            log.info(">>>>>>>>> 成功连接到服务器! <<<<<<<<<");
            // 连接成功后，自动发送登录请求
            sendLoginRequest(c, "user001", "token-is-good");
        })
        .onDisconnected(c -> {
            log.warn(">>>>>>>>> 与服务器断开连接. <<<<<<<<<");
        })
        .onMessage(ProtobufClientExample::handleMessage)
        .onError(cause -> {
            log.error(">>>>>>>>> 一个大大的错误! <<<<<<<<<", cause);
        });
        // 5. 启动客户端
        try {
            log.info("尝试连接 ...");
            client.connect().get(5, TimeUnit.SECONDS); // 阻塞等待，直到连接成功或超时
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("连接服务器失败", e);
            client.disconnect(); // 清理资源
        }
        // 6. 启动一个控制台输入线程，手动发送消息
        startConsoleInput(client);

    }

    /**
     * 发送登录请求
     * @param client
     * @param userId
     * @param token
     */
    private static void sendLoginRequest(AtomicIOClient client, String userId, String token) {
        LoginRequest loginRequestBody = LoginRequest.newBuilder()
                .setUserId(userId).setToken(token).build();
        AtomicIOMessage loginMessage = ProtobufMessage.of(ProtobufExampleCmd.LOGIN, loginRequestBody);
        log.info("Sending LoginRequest for user '{}'...", userId);
        client.send(loginMessage);
    }

    /**
     * 处理从服务器收到的消息
     */
    private static void handleMessage(AtomicIOMessage message) {
        int commandId = message.getCommandId();
        byte[] payload = message.getPayload();
        try {
            switch (commandId) {
                case ProtobufExampleCmd.LOGIN_RESPONSE:
                    LoginResponse loginResponse = LoginResponse.parseFrom(payload);
                    if (loginResponse.getSuccess()) {
                        log.info("<<<<<<<<< 登录成功! Server time: {}", loginResponse.getServerTime());
                    } else {
                        log.error("<<<<<<<<< 登录失败: {}", loginResponse.getMessage());
                    }
                    break;

                case ProtobufExampleCmd.P2P_MESSAGE_NOTIFY:
                    P2PMessageNotify notify = P2PMessageNotify.parseFrom(payload);
                    log.info("<<<<<<<<< 新消息 from [{}]: {}", notify.getFromUserId(), notify.getContent());
                    break;

                case ProtobufExampleCmd.P2P_MESSAGE_ACK:
                    // 在这里可以处理消息发送回执
                    break;

                default:
                    log.warn("<<<<<<<<< Received unhandled message with commandId: {}", commandId);
            }
        } catch (InvalidProtocolBufferException e) {
            log.error("<<<<<<<<< 解析 payload 错误, commandId: {}", commandId, e);
        }
    }

    /**
     * 启动一个线程来读取控制台输入，并发送 P2P 消息
     */
    private static void startConsoleInput(AtomicIOClient client) {
        Thread consoleThread = new Thread(() -> {
            log.info("Console input is ready. Type 'toUserId:message' to send a message.");
            Scanner scanner = new Scanner(System.in);
            while (client.isConnected()) {
                String line = scanner.nextLine();
                if (line == null || "exit".equalsIgnoreCase(line)) {
                    client.disconnect();
                    break;
                }

                String[] parts = line.split(":", 2);
                if (parts.length != 2) {
                    log.warn("Invalid format. Please use 'toUserId:message'.");
                    continue;
                }

                String toUserId = parts[0].trim();
                String content = parts[1].trim();

                P2PMessageRequest p2pRequestBody = P2PMessageRequest.newBuilder()
                        .setToUserId(toUserId)
                        .setContent(content)
                        .setClientMessageId("client-" + System.nanoTime()) // 生成一个唯一的客户端消息ID
                        .build();

                AtomicIOMessage p2pMessage = ProtobufMessage.of(ProtobufExampleCmd.P2P_MESSAGE, p2pRequestBody);

                log.info("Sending P2P message to '{}'...", toUserId);
                client.send(p2pMessage);
            }
            scanner.close();
            log.info("Console input thread stopped.");
        });
        consoleThread.setName("console-input-thread");
        consoleThread.setDaemon(true); // 设置为守护线程，主线程退出时它也退出
        consoleThread.start();
    }
}
