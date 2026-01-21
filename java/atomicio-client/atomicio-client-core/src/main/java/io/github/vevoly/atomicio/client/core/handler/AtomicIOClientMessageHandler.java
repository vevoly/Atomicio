package io.github.vevoly.atomicio.client.core.handler;

import io.github.vevoly.atomicio.client.core.internal.AtomicIOClientRequestManager;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * 客户端核心消息处理器。
 * 它的职责是将入站的 AtomicIOMessage 分流：
 * 1. 如果消息是一个请求的响应，它会通知 RequestManager 去完成对应的 Future。
 * 2. 如果消息是服务器的主动推送（如 P2P 消息、群消息、踢出通知），它会调用用户注册的监听器。
 *
 * @since 0.6.6
 * @author vevoly
 */
@Slf4j
@ChannelHandler.Sharable
public class AtomicIOClientMessageHandler extends SimpleChannelInboundHandler<AtomicIOMessage> {

    private final AtomicIOClientRequestManager requestManager;
    private final Consumer<AtomicIOMessage> onPushMessageListener; // 用户提供的业务消息监听器

    public AtomicIOClientMessageHandler(AtomicIOClientRequestManager requestManager, Consumer<AtomicIOMessage> onPushMessageListener) {
        this.requestManager = requestManager;
        this.onPushMessageListener = onPushMessageListener;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AtomicIOMessage message) throws Exception {
        // 尝试将消息作为“响应”来处理
        boolean isResponse = requestManager.completeRequest(message.getSequenceId(), message);

        // 如果它不是任何请求的响应，那么就将其视为“服务器推送”
        if (!isResponse) {
            log.debug("Received a push message from server: {}", message);
            if (onPushMessageListener != null) {
                try {
                    onPushMessageListener.accept(message);
                } catch (Exception e) {
                    log.error("Error executing push message listener for message: {}", message, e);
                }
            } else {
                log.warn("Received a push message but no listener is registered. Message: {}", message);
            }
        } else {
            log.debug("Received a response for request with sequenceId={}", message.getSequenceId());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception caught in client message handler.", cause);
        ctx.close();
    }
}
