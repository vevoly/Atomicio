package io.github.vevoly.atomicio.client.core;

import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.core.handler.*;
import io.github.vevoly.atomicio.client.core.internal.AtomicIOClientRequestManager;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.result.AuthResult;
import io.github.vevoly.atomicio.protocol.api.result.GeneralResult;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 客户端实现
 *
 * @since 0.5.0
 * @author vevoly
 */
@Slf4j
public class DefaultAtomicIOClient implements AtomicIOClient {

    @Getter
    private final AtomicIOClientConfig config;
    private final AtomicIOClientCodecProvider codecProvider;

    @Getter
    private EventLoopGroup eventLoopGroup;
    @Getter
    private Bootstrap bootstrap;
    private volatile Channel channel; // 被 I/O 线程赋值，主线程读取

    private SslContext sslContext;

    // 核心组建
    @Getter
    private final AtomicIOClientRequestManager requestManager = new AtomicIOClientRequestManager();

    // 存储用户注册的 Listeners
    private volatile Consumer<Void> onConnectedListener;
    private volatile Consumer<Void> onDisconnectedListener;
    private volatile Consumer<AtomicIOMessage> onPushMessageListener;
    private volatile Consumer<Throwable> onErrorListener;
    private volatile BiConsumer<Integer, Integer> onReconnectListener;

    // 存储已认证的 userId
    private final AtomicReference<String> currentUserId = new AtomicReference<>(null);
    // 存储已认证的 deviceId
    private final AtomicReference<String> currentDeviceId = new AtomicReference<>(null);

    public DefaultAtomicIOClient(AtomicIOClientConfig config, AtomicIOClientCodecProvider codecProvider) {
        this.config = config;
        this.codecProvider = codecProvider;
        init();
    }

    private void init() {
        try {
            if (config.getSsl().isEnabled()) {
                log.info("Client SSL/TLS is enabled.");
                File trustCertFile = new File(config.getSsl().getTrustCertPath());
                if (!trustCertFile.exists()) {
                    throw new IllegalArgumentException("SSL trust certificate file not found!");
                }
                this.sslContext = SslContextBuilder.forClient()
                        .trustManager(trustCertFile) // 告诉 SslContext 信任这个证书文件
                        .build();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to build client SslContext", e);
        }

        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();

        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis());

    }

    @Override
    public CompletableFuture<Void> connect() {
        // 动态设置 Handler
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (sslContext != null) {
                    pipeline.addLast(sslContext.newHandler(ch.alloc(), config.getServerHost(), config.getServerPort()));
                }
                // 1. 协议层, 使用 CodecProvider 构建协议栈
                codecProvider.buildPipeline(pipeline, config);
                // 2. 心跳机制层
                if (config.isHeartbeatEnabled() && config.getWriterIdleSeconds() > 0) {
                    // 从 CodecProvider 中获取心跳
                    AtomicIOMessage heartbeatMessage = codecProvider.getHeartbeat();
                    if (null == heartbeatMessage) {
                        throw new IllegalStateException("心跳已开启, 但是 CodecProvider 未找到默认心跳消息.");
                    }
                    // 2.1 Netty 空闲检测，必须是每个 channel new 一个
                    pipeline.addLast(new IdleStateHandler(0, config.getWriterIdleSeconds(),
                             0, TimeUnit.SECONDS));
                    // 2.2 心跳发送
                    pipeline.addLast(new AtomicIOHeartbeatHandler(heartbeatMessage));
                }
                // 3. 核心业务与事件翻译层
                pipeline.addLast(new AtomicIOClientChannelHandler(requestManager, DefaultAtomicIOClient.this));
                // 4. 连接管理与重连层
                if (config.isReconnectEnabled()) {
                    pipeline.addLast(new AtomicIOReconnectHandler(DefaultAtomicIOClient.this));
                }
                // 5. 全局异常处理器
                pipeline.addLast(new GlobalPipelineClientExceptionHandler(new ClientExceptionHandler(DefaultAtomicIOClient.this)));
            }
        });
        CompletableFuture<Void> connectFuture = new CompletableFuture<>();
        log.info("Connecting to {}:{}...", config.getServerHost(), config.getServerPort());
        ChannelFuture f = bootstrap.connect(config.getServerHost(), config.getServerPort());

        f.addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                this.channel = future.channel();
                log.info("Successfully connected to server.");
                connectFuture.complete(null);
                // 注意：真正的 onConnected 事件应该在 channelActive 中触发，这里只是完成了连接动作
            } else {
                log.error("Failed to connect to server.", future.cause());
                connectFuture.completeExceptionally(future.cause());
            }
        });
        return connectFuture;
    }

    @Override
    public void disconnect() {
        if (channel != null) {
            channel.close();
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
        log.info("Client disconnected.");
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    @Override
    public CompletableFuture<AuthResult> login(String userId, String token, String deviceId) {

        // 创建登录消息
        AtomicIOMessage loginMessage = codecProvider.createRequest(
                requestManager.nextSequenceId(),
                AtomicIOCommand.LOGIN_REQUEST,
                deviceId,
                userId,
                token
        );

        // 发送请求并获取响应 Future
        CompletableFuture<AtomicIOMessage> responseFuture = sendRequestAndGetResponse(loginMessage);

        // 使用 CodecProvider 解析响应
        return responseFuture.thenApply(response -> {
            try {
                AuthResult authResult = codecProvider.toAuthResult(response, userId, deviceId);
                if (authResult.success()) {
                    this.currentUserId.set(userId);
                    this.currentDeviceId.set(deviceId);
                    log.info("登录成功! Client session 已绑定 userId={} and deviceId={}.", userId, deviceId);
                }
                return authResult;
            } catch (Exception e) {
                throw new CompletionException("解析登录响应失败.", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> logout() {
        String deviceId = this.currentDeviceId.get();
        if (deviceId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("当前客户端未登录，无法登出."));
        }
        AtomicIOMessage logoutMessage = codecProvider.createRequest(
                requestManager.nextSequenceId(),
                AtomicIOCommand.LOGOUT_REQUEST,
                deviceId,
                null
        );
        // 登出成功后，清空状态
        return writeToChannel(logoutMessage).thenRun(() -> {
            this.currentDeviceId.set(null);
            this.currentUserId.set(null);
        });
    }

    @Override
    public CompletableFuture<Void> joinGroup(String groupId) {
        String deviceId = this.currentDeviceId.get();
        if (deviceId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("当前客户端未登录，无法加入群组."));
        }
        AtomicIOMessage joinMessage = codecProvider.createRequest(
                requestManager.nextSequenceId(),
                AtomicIOCommand.JOIN_GROUP_REQUEST,
                deviceId,
                groupId
        );
        return sendRequestAndGetAck(joinMessage, "加入群组失败.");
    }

    @Override
    public CompletableFuture<Void> leaveGroup(String groupId) {
        String deviceId = this.currentDeviceId.get();
        if (deviceId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("当前客户端未登录，无法退出群组."));
        }
        AtomicIOMessage leaveMessage = codecProvider.createRequest(
                requestManager.nextSequenceId(),
                AtomicIOCommand.LEAVE_GROUP_REQUEST,
                deviceId,
                groupId
        );
        return sendRequestAndGetAck(leaveMessage, "退出群组失败.");
    }

    @Override
    public CompletableFuture<Void> sendToUsers(AtomicIOMessage message, String... userIds) {
        // 这个 API 的实现依赖于协议本身是否支持在一条消息中指定多个接收者。
        // 在我们的 P2PMessageRequest 定义中，一次只能发给一个 to_user_id。
        // 因此，更真实的实现是循环发送或在业务层构建一个包含 userIds 的消息体。
        // 这里我们简化为只发送消息，由服务器处理。

        // todo 服务器端缺少发送消息
        String deviceId = this.currentDeviceId.get();
        if (deviceId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("当前客户端未登录，无法发送消息."));
        }

        if (userIds == null || userIds.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        // 1. 如果协议支持批量发送（推荐）：构建一个包含多个 userId 的请求
        // 借鉴 ioGame 的逻辑，这里发送一条 P2P_BATCH_REQUEST 到服务器
        AtomicIOMessage forwardMessage = codecProvider.createRequest(
                requestManager.nextSequenceId(),
                AtomicIOCommand.SEND_TO_USER, // 需要在你的 Command 定义中添加
                deviceId,
                userIds, // 将接收者数组放入 payload 或特定字段
                message.getPayload() // 原始业务负载
        );

        // 2. 发送并等待 ACK，确保服务器收到了转发指令
        return sendRequestAndGetAck(forwardMessage, "批量发送消息失败.");
        return writeToChannel(message);
    }

    @Override
    public CompletableFuture<Void> sendToGroup(AtomicIOMessage message, String groupId, Set<String> excludeUserIds) {
        return writeToChannel(message);
    }

    @Override
    public CompletableFuture<Void> broadcast(AtomicIOMessage message) {
        return writeToChannel(message);
    }

    // --- 事件注册方法的实现 ---
    @Override
    public void onConnected(Consumer<Void> listener) {
        this.onConnectedListener = listener;
    }

    @Override
    public void onDisconnected(Consumer<Void> listener) {
        this.onDisconnectedListener = listener;

    }

    @Override
    public void onPushMessage(Consumer<AtomicIOMessage> listener) {
        this.onPushMessageListener = listener;
    }

    @Override
    public void onError(Consumer<Throwable> listener) {
        this.onErrorListener = listener;
    }

    @Override
    public void onReconnecting(BiConsumer<Integer, Integer> listener) {
        this.onReconnectListener = listener;
    }

    public void fireConnectedEvent() {
        if (onReconnectListener != null) {
            try {
                onConnectedListener.accept(null);
            } catch (Exception e) {
                log.error("执行 onConnectedListener 时发生异常.", e);
            }
        }
    }

    public void fireDisconnectedEvent() {
        if (onDisconnectedListener != null) {
            try {
                onDisconnectedListener.accept(null);
            } catch (Exception e) {
                log.error("执行 onDisconnected listener 时发生异常.", e);
            }
        }
    }

    public void firePushMessageEvent(AtomicIOMessage message) {
        if (onPushMessageListener != null) {
            try {
                onPushMessageListener.accept(message);
            } catch (Exception e) {
                log.error("执行 onPushMessage listener 时发生异常.", e);
            }
        }
    }

    public void fireErrorEvent(Throwable cause) {
        if (onErrorListener != null) {
            try {
                onErrorListener.accept(cause);
            } catch (Exception e) {
                log.error("执行 onError listener 时发生异常.", e);
            }
        }
    }

    public void fireReconnectEvent(int attempt, int delay) {
        if (onReconnectListener != null) {
            try {
                onReconnectListener.accept(attempt, delay);
            } catch (Exception e) {
                log.error("执行 onReconnecting listener 时发生异常.", e);
            }
        }
    }

    private CompletableFuture<AtomicIOMessage> sendRequestAndGetResponse(AtomicIOMessage requestMessage) {
        if (!isConnected()) return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected."));

        CompletableFuture<AtomicIOMessage> responseFuture = requestManager.registerRequest(requestMessage.getSequenceId());

        channel.writeAndFlush(requestMessage).addListener(future -> {
            if (!future.isSuccess()) {
                requestManager.completeRequest(requestMessage.getSequenceId(), null); // 清理
                responseFuture.completeExceptionally(future.cause());
            }
        });
        return responseFuture;
    }

    private CompletableFuture<Void> sendRequestAndGetAck(AtomicIOMessage requestMessage, String errorMessagePrefix) {
        return sendRequestAndGetResponse(requestMessage).thenAccept(response -> {
            try {
                GeneralResult result = codecProvider.toGeneralResult(response);
                if (!result.success()) {
                    throw new CompletionException(new IOException(errorMessagePrefix + ": " + result.message()));
                }
            } catch (Exception e) {
                throw new CompletionException("解析 ACK response 失败.", e);
            }
        });
    }

    private CompletableFuture<Void> writeToChannel(AtomicIOMessage message) {
        if (!isConnected()) return CompletableFuture.failedFuture(new IllegalStateException("Not connected"));

        CompletableFuture<Void> writeFuture = new CompletableFuture<>();
        channel.writeAndFlush(message).addListener(future -> {
            if (future.isSuccess()) {
                writeFuture.complete(null);
            } else {
                writeFuture.completeExceptionally(future.cause());
            }
        });
        return writeFuture;
    }
}
