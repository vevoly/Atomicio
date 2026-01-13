package io.github.vevoly.atomicio.client.core;

import io.github.vevoly.atomicio.common.api.AtomicIOMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;


/**
 * 客户端心跳处理器
 *
 * @since 0.5.1
 * @author vevoly
 */
@Slf4j
@ChannelHandler.Sharable
public class AtomicIOHeartbeatHandler extends ChannelInboundHandlerAdapter {

    /**
     * 心跳消息
     * 由 CodecProvider 提供
     */
    private final AtomicIOMessage heartbeatMessage;

    public AtomicIOHeartbeatHandler(AtomicIOMessage heartbeatMessage) {
        this.heartbeatMessage = Objects.requireNonNull(heartbeatMessage, "Heartbeat message cannot be null");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.WRITER_IDLE) {
                // 写空闲时，发送心跳
                log.debug("Write idle timeout, 发送心跳包");
                ctx.writeAndFlush(this.heartbeatMessage);
            }
        } else {
            // 不关心的事件，传给 Pipeline 下一个 Handler
            super.userEventTriggered(ctx, evt);
        }

    }
}
