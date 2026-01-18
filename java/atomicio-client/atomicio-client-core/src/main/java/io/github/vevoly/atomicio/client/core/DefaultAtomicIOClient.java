package io.github.vevoly.atomicio.client.core;

import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.api.listeners.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 客户端实现
 *
 * @since 0.5.0
 * @author vevoly
 */
@Slf4j
public class DefaultAtomicIOClient implements AtomicIOClient {
    private final AtomicIOClientConfig config;
    private final AtomicIOClientCodecProvider codecProvider;

    private SslContext sslContext;
    private EventLoopGroup group;
    private Bootstrap bootstrap;
    private Channel channel;

    // 存储用户注册的 Listeners
    private final List<OnConnectedListener> connectedListeners = new CopyOnWriteArrayList<>();
    private final List<OnDisconnectedListener> disconnectedListeners = new CopyOnWriteArrayList<>();
    private final List<OnMessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<OnErrorListener> errorListeners = new CopyOnWriteArrayList<>();
    private final List<OnReconnectingListener> reconnectingListeners = new CopyOnWriteArrayList<>();

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

        this.group = new NioEventLoopGroup();
        final AtomicIOClientChannelHandler clientHandler = new AtomicIOClientChannelHandler(this);
        final AtomicIOReconnectHandler reconnectHandler = new AtomicIOReconnectHandler(this);
        this.bootstrap = new Bootstrap();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        if (sslContext != null) {
                            pipeline.addLast(sslContext.newHandler(ch.alloc(), config.getHost(), config.getPort()));
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
                        pipeline.addLast(clientHandler);

                        // 4. 连接管理与重连层
                        if (config.isReconnectEnabled()) {
                            pipeline.addLast(reconnectHandler);
                        }
                    }
                });
    }

    @Override
    public Future<Void> connect() {
        CompletableFuture<Void> connectFuture = new CompletableFuture<>();
        log.info("Connecting to {}:{}...", config.getHost(), config.getPort());
        ChannelFuture f = bootstrap.connect(config.getHost(), config.getPort());

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
        if (group != null) {
            group.shutdownGracefully();
        }
        log.info("Client disconnected.");
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    @Override
    public Future<Void> send(AtomicIOMessage message) {
        CompletableFuture<Void> sendFuture = new CompletableFuture<>();
        if (!isConnected()) {
            sendFuture.completeExceptionally(new IllegalStateException("Client is not connected."));
            return sendFuture;
        }

        channel.writeAndFlush(message).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                sendFuture.complete(null);
            } else {
                sendFuture.completeExceptionally(future.cause());
            }
        });
        return sendFuture;
    }

    // --- 事件注册方法的实现 ---
    @Override
    public AtomicIOClient onConnected(OnConnectedListener listener) {
        connectedListeners.add(listener);
        return this;
    }

    @Override
    public AtomicIOClient onDisconnected(OnDisconnectedListener listener) {
        disconnectedListeners.add(listener);
        return this;
    }

    @Override
    public AtomicIOClient onMessage(OnMessageListener listener) {
        messageListeners.add(listener);
        return this;
    }

    @Override
    public AtomicIOClient onError(OnErrorListener listener) {
        errorListeners.add(listener);
        return this;
    }

    @Override
    public AtomicIOClient onReconnecting(OnReconnectingListener listener) {
        reconnectingListeners.add(listener);
        return this;
    }

    void fireConnectedEvent() {
        connectedListeners.forEach(l -> l.onConnected(this));
    }

    void fireDisconnectedEvent() {
        disconnectedListeners.forEach(l -> l.onDisconnected(this));
    }

    void fireMessageEvent(AtomicIOMessage message) {
        messageListeners.forEach(l -> l.onMessage(message));
    }

    void fireErrorEvent(Throwable cause) {
        errorListeners.forEach(l -> l.onError(cause));
    }

    void fireReconnectingEvent(int attempt, int delay) {
        reconnectingListeners.forEach(l -> l.onReconnecting(attempt, delay));
    }

    AtomicIOClientConfig getConfig() {
        return config;
    }

    Bootstrap getBootstrap() {
        return bootstrap;
    }
    EventLoopGroup getEventLoopGroup() {
        return group;
    }
}
