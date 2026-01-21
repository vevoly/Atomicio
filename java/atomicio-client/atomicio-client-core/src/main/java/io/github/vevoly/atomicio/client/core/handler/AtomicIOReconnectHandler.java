package io.github.vevoly.atomicio.client.core.handler;

import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 重连处理器
 *
 * @since 0.5.1
 * @author vevoly
 */
@Slf4j
@ChannelHandler.Sharable
public class AtomicIOReconnectHandler extends ChannelInboundHandlerAdapter {

    private final DefaultAtomicIOClient client;
    private int reconnectAttempts = 0;

    public AtomicIOReconnectHandler(DefaultAtomicIOClient client) {
        this.client = client;
    }

@Override
public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    AtomicIOClientConfig config = client.getConfig();
    // 连接断开时，只负责启动第一次重连调度
    if (config.isReconnectEnabled() && reconnectAttempts == 0) {
        log.warn("连接丢失. 开始重连 ...");
        reconnectAttempts++;
        scheduleReconnect(config);
    }
    // 将事件继续传递
    ctx.fireChannelInactive();
}

    /**
     * 核心的重连调度方法
     */
    private void scheduleReconnect(AtomicIOClientConfig config) {
        if (!config.isReconnectEnabled()) {
            return;
        }
        // 计算延迟
        long delay = (long) config.getInitialReconnectDelaySeconds() * (1L << (reconnectAttempts - 1));
        if (delay > config.getMaxReconnectDelaySeconds()) {
            delay = config.getMaxReconnectDelaySeconds();
        }
        // 使用 client 的 EventLoop 来调度，而不是 channel 的，保证即使 channel 关闭，EventLoop 依然存活
        long finalDelay = delay;
        client.getEventLoopGroup().schedule(() -> {
            log.info("尝试重连... (attempt #{})", reconnectAttempts);
            client.fireReconnectEvent(reconnectAttempts, (int) finalDelay);

            client.getBootstrap().connect(config.getServerHost(), config.getServerPort())
                    .addListener((ChannelFuture future) -> {
                        if (future.isSuccess()) {
                            // 成功后，channelActive 会自动重置计数器
                            log.info("重连 #{} successful.", reconnectAttempts);
                        } else {
                            log.warn("重连 #{} 失败. Cause: {}. Scheduling next attempt.",
                                    reconnectAttempts, future.cause().getMessage());
                            reconnectAttempts++;
                            scheduleReconnect(config);
                        }
                    });
        }, delay, TimeUnit.SECONDS);
    }
}
