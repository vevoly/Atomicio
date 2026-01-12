package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.server.api.constants.ConnectionRejectType;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.core.manager.DisruptorManager;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务器过载保护处理器。
 * 放在 Pipeline 的最前端，负责在连接建立的早期阶段检查系统负载。
 *
 * @since 0.5.12
 * @author vevoly
 */
@Slf4j
@ChannelHandler.Sharable
public class OverloadProtectionHandler extends ChannelInboundHandlerAdapter {

    private final DefaultAtomicIOEngine engine;

    public OverloadProtectionHandler(DefaultAtomicIOEngine engine) {
        this.engine = engine;
    }

    /**
     * 在 Channel 注册到 EventLoop 时进行检查，这是最早的可靠时机。
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (isOverloaded(ctx.channel())) {
            // 如果过载，立即拒绝并关闭
            rejectAndClose(ctx);
        } else {
            // 如果未过载，将事件传递给 Pipeline 中的下一个 Handler
            super.channelRegistered(ctx);
        }
    }

    private boolean isOverloaded(Channel channel) {
        AtomicIOProperties.OverloadProtect config = engine.getConfig().getOverloadProtect();
        if (!config.isEnabled()) {
            return false;
        }

        // 检查节点总连接数
        int maxConn = config.getTotalConnect();
        if (maxConn > 0) {
            int currentConn = engine.getSessionManager().getTotalConnectCount();
            if (currentConn >= maxConn) {
                log.warn("Node overloaded: Total connection count ({}) has reached the limit ({}). Rejecting new connection from {}.",
                        currentConn, maxConn, channel.remoteAddress());
                engine.getEventManager().fireConnectionRejectEvent(channel, ConnectionRejectType.SERVER_OVERLOADED, null);
                return true;
            }
        }

        // 检查 Disruptor 队列容量
        int minPercent = config.getQueueMinPercent();
        if (minPercent > 0) {
            DisruptorManager disruptorManager = engine.getDisruptorManager();
            // 获取队列的总容量和剩余容量
            long bufferSize = disruptorManager.getBufferSize();
            long remainingCapacity = disruptorManager.getRemainingCapacity();
            // 进行有效性检查和计算
            if (bufferSize > 0) {
                // 计算剩余容量的百分比
                double remainingPercent = (double) remainingCapacity / bufferSize * 100.0;
                // 如果剩余容量低于我们配置的最小阈值，则认为过载
                if (remainingPercent < minPercent) {
                    log.warn("Node overloaded: Disruptor queue is almost full (remaining: {}%, threshold: {}%). Rejecting new connection from {}.",
                            String.format("%.2f", remainingPercent), minPercent, channel.remoteAddress());
                    // 触发拒绝事件
                    engine.getEventManager().fireConnectionRejectEvent(channel, ConnectionRejectType.SERVER_OVERLOADED, null);
                    return true;
                }
            }
        }
        return false;
    }

    private void rejectAndClose(ChannelHandlerContext ctx) {
        ctx.channel().config().setOption(ChannelOption.SO_LINGER, 0);
        ctx.close();
    }

}
