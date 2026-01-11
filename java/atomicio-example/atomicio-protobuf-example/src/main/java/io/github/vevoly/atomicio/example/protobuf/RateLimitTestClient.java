package io.github.vevoly.atomicio.example.protobuf;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ip 速率限制测试客户端
 *
 * @since 0.5.11
 * @author vevoly
 */
@Slf4j
public class RateLimitTestClient {

    public static void main(String[] args) throws InterruptedException {
        // --- 测试参数 ---
        final String host = "127.0.0.1";
        final int port = 8308; // 确保与服务器配置的端口一致
        final int connectionAttempts = 15; // 总共尝试连接的次数

        log.info("Starting Rate Limit Test...");
        log.info("Target: {}:{}", host, port);
        log.info("Total connection attempts: {}", connectionAttempts);
        log.warn("Please ensure the server has a rate limit configured, e.g., 10 connections / 60 seconds.");

        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // 客户端的 Pipeline 可以非常简单，因为我们只关心连接本身
                    }
                });

        // 使用 CountDownLatch 来等待所有连接尝试完成
        CountDownLatch latch = new CountDownLatch(connectionAttempts);

        for (int i = 1; i <= connectionAttempts; i++) {
            final int attemptNumber = i;

            // 发起异步连接
            ChannelFuture connectFuture = bootstrap.connect(host, port);

            connectFuture.addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    Channel ch = future.channel();
                    // 给 Channel 增加一个标志位，判断是我们主动关的，还是被动断的
                    final AtomicBoolean kickedByServer = new AtomicBoolean(true);

                    // 监听连接断开事件
                    ch.closeFuture().addListener(f -> {
                        if (kickedByServer.get()) {
                            log.error("Attempt #{} - Connection REJECTED by server (Closed immediately).", attemptNumber);
                        } else {
                            log.info("Attempt #{} - Connection closed normally.", attemptNumber);
                        }
                        latch.countDown();
                    });

                    // 模拟业务等待 200ms
                    // 如果 200ms 内 closeFuture 触发了，说明是被服务器限流踢掉的
                    ch.eventLoop().schedule(() -> {
                        if (ch.isActive()) {
                            kickedByServer.set(false); // 标记为正常连接
                            log.info("Attempt #{} - Connection STABLE (Passed rate limit).", attemptNumber);
                            ch.close(); // 正常业务处理完后关闭
                        }
                    }, 200, TimeUnit.MILLISECONDS);

                } else {
                    log.error("Attempt #{} - Connection FAILED. Cause: {}", attemptNumber, future.cause().getMessage());
                    latch.countDown();
                }
            });

            // 在两次连接之间加入一个极短的延迟，模拟真实的网络情况
            Thread.sleep(500);
        }

        // 等待所有连接尝试结束
        latch.await();

        // 清理资源
        group.shutdownGracefully();
        log.info("Rate Limit Test finished.");
    }
}