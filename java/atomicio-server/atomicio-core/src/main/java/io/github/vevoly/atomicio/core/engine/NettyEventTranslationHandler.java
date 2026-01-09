package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.api.constants.IdleState;
import io.github.vevoly.atomicio.core.event.DisruptorManager;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty 事件翻译处理器
 * 负责将 Netty 的底层事件 (channelActive, channelInactive, channelRead)
 * 翻译成引擎的顶层事件 (CONNECT, DISCONNECT, MESSAGE)。
 *
 * @author vevoly
 * @since 0.0.1
 */
@Slf4j
@AllArgsConstructor
@ChannelHandler.Sharable
public class NettyEventTranslationHandler extends ChannelInboundHandlerAdapter {

    // 静态常量，保证所有 Channel 使用同一个 Key
    public static final AttributeKey<AtomicIOSession> SESSION_KEY = AttributeKey.valueOf("atomicio.session");

    private final DisruptorManager disruptorManager;
    private final DefaultAtomicIOEngine engine;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // 在 Handler 被添加到 Pipeline 时，就创建 Session
        AtomicIOSession session = new NettySession(ctx.channel(), engine);
        ctx.channel().attr(SESSION_KEY).set(session);
        super.handlerAdded(ctx);
    }

    /**
     * 当一个连接建立时被调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 1. 将 Netty Channel 封装成我们的 AtomicIOSession
        AtomicIOSession session = ctx.channel().attr(SESSION_KEY).get();
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
        AtomicIOSession session = ctx.channel().attr(SESSION_KEY).getAndSet(null); // 获取并清除
        if (session != null) {
            // 触发引擎的 DISCONNECT 事件
            disruptorManager.publishEvent(AtomicIOEventType.DISCONNECT, session, null, null);
        }
        super.channelInactive(ctx);
    }

    /**
     * 当收到数据时被调用
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        AtomicIOSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session != null && msg instanceof AtomicIOMessage) {
            // 触发引擎的 MESSAGE 事件
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
        AtomicIOSession session = ctx.channel().attr(SESSION_KEY).getAndSet(null); // 获取并清除
        if (session != null) {
            log.warn("服务器抛出异常 session {}: {}", session.getId(), cause.getMessage());
            // 发布 ERROR 事件
            disruptorManager.publishEvent(AtomicIOEventType.ERROR, session, null, cause);
        } else {
            // session 还没创建就出错（比如 SSL 握手早期）
            log.warn("Session 创建前捕获 channel 异常, channelId: {} : {}", ctx.channel().id(), cause.getMessage());
        }
        ctx.close();
    }

    /**
     * 捕获 IdleEventStateEvent 事件
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState myIdleState;
            switch (((IdleStateEvent) evt).state()) {
                case READER_IDLE:
                    myIdleState = IdleState.READER_IDLE;
                    break;
                case WRITER_IDLE:
                    myIdleState = IdleState.WRITER_IDLE;
                    break;
                case ALL_IDLE:
                    myIdleState = IdleState.ALL_IDLE;
                    break;
                default:
                    return;
            }
            disruptorManager.publishIdleEvent(new NettySession(ctx.channel(), engine), myIdleState);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

}
