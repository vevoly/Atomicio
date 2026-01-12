package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.server.api.constants.AtomicIOEventType;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.server.api.constants.IdleState;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.core.manager.DisruptorManager;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty Channel Pipeline 核心事件翻译处理器
 * 它的唯一职责是将 Netty 的底层 I/O 事件，翻译成 DisruptorEntry，
 * 并发布到 Disruptor 中进行异步处理。
 *
 * @author vevoly
 * @since 0.0.1
 */
@Slf4j
@ChannelHandler.Sharable
public class NettyEventTranslationHandler extends ChannelInboundHandlerAdapter {

    // 静态常量，保证所有 Channel 使用同一个 Key
    public static final AttributeKey<AtomicIOSession> SESSION_KEY = AttributeKey.valueOf(AtomicIOConstant.IO_SESSION_KEY_NAME);

    private final DisruptorManager disruptorManager;
    private final DefaultAtomicIOEngine engine;

    public NettyEventTranslationHandler(DisruptorManager disruptorManager, DefaultAtomicIOEngine engine) {
        this.disruptorManager = disruptorManager;
        this.engine = engine;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // 在 Handler 被添加到 Pipeline 时，就创建 Session
        final AtomicIOSession session = new NettySession(ctx.channel(), engine);
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
        final AtomicIOSession session = ctx.channel().attr(SESSION_KEY).get();
        // 2. 触发引擎的 CONNECT 事件
        if (session != null) {
            disruptorManager.publish(disruptorEntry -> {
                disruptorEntry.setType(AtomicIOEventType.CONNECT);
                disruptorEntry.setSession(session);
            });
        }
        super.channelActive(ctx);
    }

    /**
     * 当一个连接断开时被调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        final AtomicIOSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session != null) {
            // 1. 立即、同步地执行状态清理
            engine.getSessionManager().unbindUserInternal(session);
            // 发布异步 DISCONNECT 事件
            disruptorManager.publish(disruptorEntry -> {
                disruptorEntry.setType(AtomicIOEventType.DISCONNECT);
                disruptorEntry.setSession(session);
            });
        }
        super.channelInactive(ctx);
    }

    /**
     * 当 Handler 被移除时被调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // 在 Handler 移除时，进行最终的清理
        ctx.channel().attr(SESSION_KEY).set(null);
        super.handlerRemoved(ctx);
    }

    /**
     * 当收到数据时被调用
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final AtomicIOSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session != null && msg instanceof AtomicIOMessage) {
            // 触发引擎的 MESSAGE 事件
            disruptorManager.publish(disruptorEntry -> {
                disruptorEntry.setType(AtomicIOEventType.MESSAGE);
                disruptorEntry.setSession(session);
                disruptorEntry.setMessage((AtomicIOMessage) msg);
            });
        } else {
            log.warn("Received an unhandled message type: {} from session {}",
                    msg.getClass().getName(), session.getId());
            // 确保释放未处理当消息，防止内存泄漏
            ReferenceCountUtil.release(msg);
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
        final AtomicIOSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session != null) {
            log.warn("服务器抛出异常 session {}: {}", session.getId(), cause.getMessage());
            // 发布 ERROR 事件
            disruptorManager.publish(disruptorEntry -> {
                disruptorEntry.setType(AtomicIOEventType.ERROR);
                disruptorEntry.setSession(session);
                disruptorEntry.setCause(cause);
            });
        } else {
            // session 还没创建就出错（比如 SSL 握手早期）
            log.warn("Session 创建前捕获 channel 异常, channelId: {} : {}", ctx.channel().id(), cause.getMessage());
        }
        ctx.close(); // 发生异常时，关闭连接
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
            final AtomicIOSession session = ctx.channel().attr(SESSION_KEY).get();
            if (session != null) {
                final IdleState myIdleState = translateIdleState(((IdleStateEvent) evt).state());
                if (myIdleState != null) {
                    disruptorManager.publish(disruptorEntry -> {
                        disruptorEntry.setType(AtomicIOEventType.IDLE);
                        disruptorEntry.setSession(session);
                        disruptorEntry.setIdleState(myIdleState);
                    });
                }
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 翻译空闲状态
     * @param nettyState
     * @return
     */
    private IdleState translateIdleState(io.netty.handler.timeout.IdleState nettyState) {
        switch (nettyState) {
            case READER_IDLE: return IdleState.READER_IDLE;
            case WRITER_IDLE: return IdleState.WRITER_IDLE;
            case ALL_IDLE: return IdleState.ALL_IDLE;
            default: return null;
        }
    }
}
