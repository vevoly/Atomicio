package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.api.codec.AtomicIOCodecProvider;
import io.github.vevoly.atomicio.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.core.handler.*;
import io.github.vevoly.atomicio.core.ssl.SslContextFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Netty 服务管理器
 *
 * @since 0.5.8
 * @author vevoly
 */
@Slf4j
public class NettyServerManager {

    private final DefaultAtomicIOEngine engine;
    private final AtomicIOProperties config;
    private final AtomicIOCodecProvider codecProvider;

    // Netty核心组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;

    // 共享处理器
    private SslContext sslContext; // ssl 上下文
    private IpConnectionLimitHandler ipConnectionLimitHandler; // Ip 连接数限制处理器
    private IpRateLimitHandler ipRateLimitHandler; // Ip 连接速率限制处理器
    private SslExceptionHandler sslExceptionHandler; // SSL 异常处理器
    private NettyEventTranslationHandler nettyEventTranslationHandler; // Netty 事件翻译处理器
    private HeartbeatResponseHandler heartbeatResponseHandler; // 心跳响应处理器
    private OverloadProtectionHandler overloadProtectionHandler; // 服务器负载保护处理器

    private final ChannelInitializer<SocketChannel> childHandlerInitializer;

    public NettyServerManager(DefaultAtomicIOEngine engine) {
        this.engine = engine;
        this.config = engine.getConfig();
        this.codecProvider = engine.getCodecProvider();
        // 初始化所有需要共享的 Handler 实例
        initializeHandlers();
        // 创建 ChannelInitializer
        this.childHandlerInitializer = new ServerChannelInitializer();
    }

    public Future<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // 初始化 Netty 线程组
        bossGroup = new NioEventLoopGroup(config.getBossThreads());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        try {
            // 配置和启动 Netty ServerBootstrap
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(this.childHandlerInitializer);
            log.info("Netty server starting on port {}...", config.getPort());
            serverChannelFuture = bootstrap.bind(config.getPort()).sync(); // 绑定端口
            log.info("Netty server bound successfully to port {}, codec: {}",
                    config.getPort(), codecProvider.getClass().getSimpleName());
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        }
        return future;
    }

    public void stop() {
        log.info("Netty Server shutting down...");
        // 关闭顺序与启动相反
        if (serverChannelFuture != null) {
            serverChannelFuture.channel().close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        log.info("Netty Server shutdown complete.");
    }

    /**
     * 初始化处理器
     */
    private void initializeHandlers() {
        if (config.getOverloadProtect().isEnabled()) {
            log.info("初始化服务器过载保护处理器 ...");
            this.overloadProtectionHandler = new OverloadProtectionHandler(engine);
        }
        if (config.getIpSecurity().getMaxConnect() > 0) {
            log.info("初始化 IP 连接数限制处理器 ({}) ...", config.getIpSecurity().getMaxConnect());
            this.ipConnectionLimitHandler = new IpConnectionLimitHandler(engine);
        }
        if (config.getIpSecurity().getRateLimitCount() > 0 && config.getIpSecurity().getRateLimitInterval() > 0) {
            log.info("初始化 IP 速率限制处理器 ({} conn/{}s) ...",
                    config.getIpSecurity().getRateLimitCount(), config.getIpSecurity().getRateLimitInterval());
            this.ipRateLimitHandler = new IpRateLimitHandler(engine);
        }
        if (config.getSsl().isEnabled()) {
            log.info("初始化 SSL/TLS 上下文和 SSL/TLS 异常处理器 ...");
            this.sslContext = SslContextFactory.createSslContext(config.getSsl());
            this.sslExceptionHandler = new SslExceptionHandler(engine);
        }
        log.info("初始化 Netty 事件翻译处理器 ...");
        this.nettyEventTranslationHandler = new NettyEventTranslationHandler(engine.getDisruptorManager(), engine);
        log.info("初始化 ❤️心跳回忆处理器 ...");
        this.heartbeatResponseHandler = new HeartbeatResponseHandler(engine);


    }

    /**
     * 私有的内部类来负责 Pipeline 构建
     */
    private class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            // 过载保护
            if (overloadProtectionHandler != null) {
                pipeline.addLast(overloadProtectionHandler);
            }
            // 准入控制层 （IP 过滤）
            if (ipRateLimitHandler != null) {
                pipeline.addLast(ipRateLimitHandler); // 速率限制
            }
            if (ipConnectionLimitHandler != null) {
                pipeline.addLast(ipConnectionLimitHandler); // 连接数限制
            }
            // 加密层 (SSL/TLS)
            if (sslContext != null) {
                pipeline.addLast(sslContext.newHandler(ch.alloc()));
                pipeline.addLast(sslExceptionHandler);
            }
            // 协议编解码层
            codecProvider.buildPipeline(pipeline, config.getCodec().getMaxFrameLength());
            // 协议编解码层
            // 心跳回应
            pipeline.addLast(heartbeatResponseHandler);
            // 空闲检测
            int readerIdleSeconds = config.getSession().getReadIdleSeconds();
            if (readerIdleSeconds > 0) {
                pipeline.addLast(new IdleStateHandler(readerIdleSeconds, config.getSession().getWriteIdleSeconds(),
                        config.getSession().getAllIdleSeconds(), TimeUnit.SECONDS));
            }
            // 事件翻译层
            pipeline.addLast(nettyEventTranslationHandler);
        }
    }
}
