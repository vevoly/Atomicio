package io.github.vevoly.atomicio.client.core;

import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.github.vevoly.atomicio.api.codec.AtomicIOCodecProvider;
import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.api.listeners.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

/**
 * 客户端实现
 *
 * @since 0.5.0
 * @author vevoly
 */
@Slf4j
public class DefaultAtomicIOClient implements AtomicIOClient {
    private final AtomicIOClientConfig config;
    private final AtomicIOCodecProvider codecProvider;

    private EventLoopGroup group;
    private Bootstrap bootstrap;
    private Channel channel;

    // 存储用户注册的 Listeners
    private final List<OnConnectedListener> connectedListeners = new CopyOnWriteArrayList<>();
    private final List<OnDisconnectedListener> disconnectedListeners = new CopyOnWriteArrayList<>();
    private final List<OnMessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<OnErrorListener> errorListeners = new CopyOnWriteArrayList<>();
    private final List<OnReconnectingListener> reconnectingListeners = new CopyOnWriteArrayList<>();

    public DefaultAtomicIOClient(AtomicIOClientConfig config, AtomicIOCodecProvider codecProvider) {
        this.config = config;
        this.codecProvider = codecProvider;
        init();
    }

    private void init() {
        this.group = new NioEventLoopGroup();
        final AtomicIOClientChannelHandler clientHandler = new AtomicIOClientChannelHandler(this);
        this.bootstrap = new Bootstrap();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        // 1. 使用 CodecProvider 构建协议栈
                        codecProvider.buildPipeline(pipeline);

                        // todo 2. 添加心跳和重连 Handler (暂时留空，后续实现)
                        // pipeline.addLast(new IdleStateHandler(...));
                        // pipeline.addLast(new ReconnectHandler(DefaultAtomicIOClient.this));

                        // 3. 添加我们的核心逻辑 Handler
                        pipeline.addLast(clientHandler);
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
}
