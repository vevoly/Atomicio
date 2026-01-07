package io.github.vevoly.atomicio.client.core;

import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端的核心业务处理器。
 * 负责将 Netty 事件翻译为客户端 SDK 的事件。
 *
 * @since 0.5.0
 * @author vevoly
 */
@Slf4j
@AllArgsConstructor
@ChannelHandler.Sharable
public class AtomicIOClientChannelHandler extends SimpleChannelInboundHandler<AtomicIOMessage> {

    private final DefaultAtomicIOClient client;

    /**
     * 连接建立时，触发 onConnected 事件
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        client.fireConnectedEvent();
        super.channelActive(ctx);
    }

    /**
     * 连接断开时，触发 onDisconnected 事件
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        client.fireDisconnectedEvent();
        super.channelInactive(ctx);
    }

    /**
     * 收到消息时，触发 onMessage 事件
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AtomicIOMessage msg) throws Exception {
        client.fireMessageEvent(msg);
    }

    /**
     * 发生异常时，触发 onError 事件
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        client.fireErrorEvent(cause);
        ctx.close();
    }
}
