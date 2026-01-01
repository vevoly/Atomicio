package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.core.event.DisruptorManager;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty Channel Pipeline 核心处理器。
 * 负责将 Netty 的底层事件 (channelActive, channelInactive, channelRead)
 * 翻译成我们引擎的顶层事件 (CONNECT, DISCONNECT, MESSAGE)。
 *
 * @author vevoly
 * @since 0.0.1
 */
@Slf4j
@AllArgsConstructor
public class EngineChannelHandler extends ChannelInboundHandlerAdapter {

    private final DisruptorManager disruptorManager;

    /**
     * 当一个连接建立时被调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 1. 将 Netty Channel 封装成我们的 AtomicIOSession
        AtomicIOSession session = new NettySession(ctx.channel());
        // 2. 触发引擎的 CONNECT 事件
        disruptorManager.publishEvent(AtomicIOEventType.CONNECT, session, null, null);
        super.channelActive(ctx);
    }

    /**
     * 当一个连接断开时被调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        AtomicIOSession session = new NettySession(ctx.channel());
        // 1. 获取并清理用户绑定关系
        // 2. 触发引擎的 DISCONNECT 事件
        disruptorManager.publishEvent(AtomicIOEventType.DISCONNECT, session, null, null);
        super.channelActive(ctx);
    }

    /**
     * 当收到数据时被调用
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        AtomicIOSession session = new NettySession(ctx.channel());
        if (msg instanceof AtomicIOMessage) {
            disruptorManager.publishEvent(AtomicIOEventType.MESSAGE, session, (AtomicIOMessage) msg, null);
        } else {
            log.warn("Received an unhandled message type: {} from session {}",
                    msg.getClass().getName(), session.getId());
        }
    }

    /**
     * 当发生异常时被调用
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        AtomicIOSession session = new NettySession(ctx.channel());
        disruptorManager.publishEvent(AtomicIOEventType.ERROR, session, null, cause);
        ctx.close();
    }

}
