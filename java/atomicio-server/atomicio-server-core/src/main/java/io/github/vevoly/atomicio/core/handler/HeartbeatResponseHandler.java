package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 心跳回忆处理器
 *
 * @since 0.5.10
 */
@Slf4j
@ChannelHandler.Sharable
public class HeartbeatResponseHandler extends SimpleChannelInboundHandler<AtomicIOMessage> {

    private final DefaultAtomicIOEngine engine;

    public HeartbeatResponseHandler(DefaultAtomicIOEngine engine) {
        this.engine = engine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AtomicIOMessage message) throws Exception {
        if (message.getCommandId() == AtomicIOCommand.HEARTBEAT) {
            handleHeartbeat(ctx, message);
        } else {
            // 如果不是心跳，就原封不动地传递给下一个 Handler
            ctx.fireChannelRead(message);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception caught in HeartbeatResponseHandler.", cause);
        ctx.fireExceptionCaught(cause);
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, AtomicIOMessage requestMessage) {
        // 从 Channel 属性中获取 Session
        AtomicIOSession session = ctx.channel().attr(NettyEventTranslationHandler.SESSION_KEY).get();
        if (session == null) {
            return;
        }
        log.info("Received 💗 from session {}, responding.", session.getId());
        // 从引擎获取当前的 CodecProvider
        AtomicIOServerCodecProvider codecProvider = engine.getCodecProvider();
        // 委托给 CodecProvider 创建回应
        AtomicIOMessage response = codecProvider.createHeartbeatResponse(requestMessage);
        if (response != null) {
            session.send(response);
        }
    }

}
