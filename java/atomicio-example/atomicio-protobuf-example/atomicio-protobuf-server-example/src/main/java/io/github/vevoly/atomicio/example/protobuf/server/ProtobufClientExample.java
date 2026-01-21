//package io.github.vevoly.atomicio.example.protobuf;
//
//import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
//import io.github.vevoly.atomicio.client.codec.protobuf.ProtobufClientCodecProvider;
//import io.github.vevoly.atomicio.example.protobuf.proto.*;
//import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
//import io.github.vevoly.atomicio.client.api.AtomicIOClient;
//import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
//import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
//import io.github.vevoly.atomicio.example.protobuf.cmd.BusinessCommand;
//import lombok.extern.slf4j.Slf4j;
//
//import java.util.Scanner;
//import java.util.concurrent.TimeUnit;
//
//@Slf4j
//public class ProtobufClientExample {
//
//    public static void main(String[] args) {
//
//        // 1. 配置客户端
//        AtomicIOClientConfig config = new AtomicIOClientConfig(); // 使用默认配置
//        config.getSsl().setTrustCertFromResource("server.crt"); // 信任服务端证书
//        config.setMaxFrameLength(100);
//        config.setServerPort(8309);
//        // 2. 选择解码器
//        AtomicIOClientCodecProvider codecProvider = new ProtobufClientCodecProvider();
//        // 3.创建客户端实例
//        AtomicIOClient client = new DefaultAtomicIOClient(config, codecProvider);
//        // 4. 注册事件监听器
//        client.onConnected(v -> log.info(">>>>>>>>> 成功连接到服务器! <<<<<<<<<"));
//        client.onDisconnected(v -> log.warn(">>>>>>>>> 与服务器断开连接. <<<<<<<<<"));
//        client.onError(cause -> log.error(">>>>>>>>> 一个大大的错误! <<<<<<<<<", cause));
//        // 只监听服务器主动推送的业务消息
//        client.onPushMessage(ProtobufClientExample::handlePushMessage);
//
//        // 5. 启动客户端
//        try {
//            log.info("尝试连接 ...");
//            client.connect().get(5, TimeUnit.SECONDS); // 阻塞等待，直到连接成功或超时
//        } catch (Exception e) {
//            log.error("连接服务器失败", e);
//            client.disconnect(); // 清理资源
//            return;
//        }
//        // 6. 启动一个控制台输入线程，手动发送消息
//        startConsoleInput(client);
//
//    }
//
//    /**
//     * 只处理服务器主动推送的消息
//     */
//    private static void handlePushMessage(AtomicIOMessage message) {
//        try {
//            switch (message.getCommandId()) {
//                // 不再需要处理 LOGIN_RESPONSE 或其他请求的响应
//                case BusinessCommand.P2P_MESSAGE:
//                    // 假设业务层定义了 P2PMessageRequest 是用于发送和接收的
//                    P2PMessageRequest notify = message.getPayloadAs(P2PMessageRequest.class);
//                    log.info("<<<<<<<<< 新消息 from [{}]: {}", notify.getToUserId(), notify.getContent());
//                    break;
//
//                // 可以处理其他推送通知，例如群消息、被踢下线等
//                // case BusinessCommand.GROUP_MESSAGE: ...
//                // case AtomicIOCommand.KICK_OUT_NOTIFY: ...
//
//                default:
//                    log.warn("<<<<<<<<< 收到未处理的推送消息，指令ID: {}", message.getCommandId());
//            }
//        } catch (Exception e) {
//            log.error("<<<<<<<<< 解析推送消息 payload 错误, 指令ID: {}", message.getCommandId(), e);
//        }
//    }
//
//    /**
//     * ★ 重构：启动控制台输入线程
//     */
//    private static void startConsoleInput(AtomicIOClient client, String fromUserId) {
//        Thread consoleThread = new Thread(() -> {
//            log.info("控制台已就绪. 输入 'toUserId:message' 来发送消息.");
//            Scanner scanner = new Scanner(System.in);
//            while (client.isConnected()) {
//                String line = scanner.nextLine();
//                if (line == null || "exit".equalsIgnoreCase(line)) {
//                    client.disconnect();
//                    break;
//                }
//
//                String[] parts = line.split(":", 2);
//                if (parts.length != 2) {
//                    log.warn("格式错误. 请使用 'toUserId:message'.");
//                    continue;
//                }
//
//                String toUserId = parts[0].trim();
//                String content = parts[1].trim();
//
//                // ★★★ 使用新的、协议无关的业务层 API ★★★
//                sendP2PMessage(client, fromUserId, toUserId, content);
//            }
//            scanner.close();
//            log.info("控制台输入线程已停止.");
//        });
//        consoleThread.setName("console-input-thread");
//        consoleThread.setDaemon(true);
//        consoleThread.start();
//    }
//
//    /**
//     * ★ 新方法：封装业务层的 P2P 消息发送逻辑
//     */
//    private static void sendP2PMessage(AtomicIOClient client, String fromUserId, String toUserId, String content) {
//        // 1. 创建业务相关的 Protobuf 载体
//        P2PMessageRequest p2pPayload = P2PMessageRequest.newBuilder()
//                .setClientMessageId(UUID.randomUUID().toString())
//                .setToUserId(toUserId) // 接收方是 toUserId
//                .setContent(fromUserId + ":" + content) // 载体里可以带上发送方
//                .build();
//
//        // 2. 使用 CodecProvider 将业务载体包装成框架能理解的 AtomicIOMessage
//        AtomicIOMessage messageToSend = ((DefaultAtomicIOClient) client).getCodecProvider().createRequest(
//                0, // 发后不理，sequenceId 可以为 0
//                BusinessCommand.P2P_MESSAGE,
//                null,
//                p2pPayload
//        );
//
//        // 3. 调用框架提供的通用路由 API
//        log.info("发送 P2P 消息 to '{}'...", toUserId);
//        client.sendToUsers(messageToSend, toUserId);
//    }
//}
