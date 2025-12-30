package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.listeners.ErrorEventListener;
import io.github.vevoly.atomicio.api.listeners.MessageEventListener;
import io.github.vevoly.atomicio.api.listeners.SessionEventListener;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

/**
 * AtomicIOEngine 的默认实现。
 * 负责启动和管理 Netty 服务器，以及所有会话的生命周期。
 *
 * @since 0.0.1
 * @author vevoly
 */
@Slf4j
public class DefaultAtomicIOEngine implements AtomicIOEngine {

    private final int port;

    // Netty核心组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;

    private final Map<AtomicIOEventType, List<SessionEventListener>> sessionListeners = new ConcurrentHashMap<>();
    private final List<MessageEventListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<ErrorEventListener> errorListeners = new CopyOnWriteArrayList<>();

    public DefaultAtomicIOEngine(int port) {
        this.port = port;
    }

    @Override
    public Future<Void> start() {
        CompletableFuture<Void> startFuture = new CompletableFuture<>();
        // 初始化 Netty 线程组
        bossGroup = new NioEventLoopGroup(1);  // 负责接受连接
        workerGroup = new NioEventLoopGroup();          // 负责处理 I/O 操作

        try {
            // 启动 Netty 服务器
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        // 每当有一个新的连接被 bossGroup 接受后，就初始化这个新连接的 ChannelPipeline
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            // 定义 ChannelPipeline
                            // 1. 在这里添加各种 Netty 内置的编解码器，例如处理日志、SSL、心跳等
//                            socketChannel.pipeline().addLast(new LoggingHandler(LogLevel.INFO));

                            // 2. 添加我们自定义的核心处理器，事件翻译的枢纽
                            socketChannel.pipeline().addLast(new EngineChannelHandler(DefaultAtomicIOEngine.this));

                            log.info("New channel initialized: {}", socketChannel.remoteAddress());
                        }
                    });
            log.info("Atomicio Engine starting on port {}...", port);
            // 绑定端口
            serverChannelFuture = bootstrap.bind(port).sync();
            serverChannelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    log.info("Atomicio Engine started successfully on port {}.", port);
                    startFuture.complete(null);
                } else {
                    log.error("Failed to start Atomicio Engine on port {}. Cause: {}", port, future.cause());
                    startFuture.completeExceptionally(future.cause());
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            startFuture.completeExceptionally(new RuntimeException("Engine start failed", e));
        }
        return startFuture;
    }

    @Override
    public void shutdown() {
        log.info("Atomicio Engine shutting down...");
        if (serverChannelFuture != null) {
            serverChannelFuture.channel().close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        log.info("Atomicio Engine shutdown gracefully.");
    }

    @Override
    public void on(AtomicIOEventType eventType, SessionEventListener listener) {

    }

    @Override
    public void onMessage(MessageEventListener listener) {

    }

    @Override
    public void onError(ErrorEventListener listener) {

    }


    @Override
    public void bindUser(String userId, AtomicIOSession session) {

    }

    @Override
    public void sendToUser(String userId, AtomicIOMessage message) {

    }

    @Override
    public void joinGroup(String groupId, String userId) {

    }

    @Override
    public void leaveGroup(String groupId, String userId) {

    }

    @Override
    public void sendToGroup(String groupId, AtomicIOMessage message, String... excludeUserIds) {

    }

    @Override
    public void broadcast(AtomicIOMessage message) {

    }
}
