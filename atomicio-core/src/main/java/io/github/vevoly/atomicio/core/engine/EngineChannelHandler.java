package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.api.AtomicIOSession;
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

    private final DefaultAtomicIOEngine engine;

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

        log.debug("Session CONNECTED -{}", session.getId());
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
        log.debug("Session CONNECTED -{}", session.getId());
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

        // 这里的 msg 已经是解码后的 AtomicIOMessage 对象了（假设 Pipeline 中有解码器）
        // AtomicIOMessage message = (AtomicIOMessage) msg;

        // 触发引擎的 MESSAGE 事件 (下一步实现)
        // engine.fireEvent(AtomicIOEventType.MESSAGE, session, message);
        log.debug("Session MESSAGE -{} : {}", session.getId(), msg);
    }

    /**
     * 当发生异常时被调用
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 当发生异常时被调用
        AtomicIOSession session = new NettySession(ctx.channel());

        // 触发引擎的 ERROR 事件 (下一步实现)
        // engine.fireEvent(AtomicIOEventType.ERROR, session, cause);

        log.error("Session EXCEPTION -{} : {}", session.getId(), cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

}
