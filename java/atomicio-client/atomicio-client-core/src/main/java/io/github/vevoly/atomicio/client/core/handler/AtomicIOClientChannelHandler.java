package io.github.vevoly.atomicio.client.core.handler;

import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
import io.github.vevoly.atomicio.client.core.internal.AtomicIOClientRequestManager;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 客户端的核心业务处理器。
 * 1. 【消息路由】将入站的 AtomicIOMessage 智能分流为“响应”和“推送”。
 * 2. 【生命周期】将 Netty 的 Channel 生命周期事件 (Active, Inactive, Exception) 翻译为客户端的事件。
 *
 * @since 0.5.0
 * @author vevoly
 */
@Slf4j
@AllArgsConstructor
@ChannelHandler.Sharable
public class AtomicIOClientChannelHandler extends SimpleChannelInboundHandler<AtomicIOMessage> {

    private final AtomicIOClientRequestManager requestManager;
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
        // 当连接断开时，清理挂起的请求并触发事件
        client.getRequestManager().clear(new IOException("连接断开"));
        client.fireDisconnectedEvent();
        super.channelInactive(ctx);
    }

    /**
     * 收到消息时，触发 onMessage 事件
     *
     * @param ctx
     * @param message
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AtomicIOMessage message) throws Exception {
        boolean isResponse = requestManager.completeRequest(message.getSequenceId(), message);

        // 如果不是任何请求的响应，那么就将其视为服务器推送
        if (!isResponse) {
            log.debug("Received a push message from server: {}", message);
            // 通过 client 实例来触发 onPushMessage 事件
            client.firePushMessageEvent(message);
        } else {
            log.debug("Received a response for request with sequenceId={}", message.getSequenceId());
        }
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
