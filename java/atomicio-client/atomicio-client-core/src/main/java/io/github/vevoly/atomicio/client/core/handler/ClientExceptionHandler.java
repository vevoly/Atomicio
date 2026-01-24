package io.github.vevoly.atomicio.client.core.handler;

import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
import io.github.vevoly.atomicio.common.api.exception.AtomicIOExceptionHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端全局同步异常处理器
 *
 * @since 0.6.8
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
public class ClientExceptionHandler implements AtomicIOExceptionHandler {

    private final DefaultAtomicIOClient client;

    @Override
    public void handle(Object context, Throwable cause) {
        if (context instanceof ChannelHandlerContext) {
            ChannelHandlerContext ctx = (ChannelHandlerContext) context;
            log.error("Unhandled exception in CLIENT pipeline for channel [{}]: {}",
                    ctx.channel().id(), cause.getMessage());
            // 客户端的逻辑：触发 onError 事件
            client.fireErrorEvent(cause);

            ctx.close();
        } else {
            // 处理非 Pipeline 的异常
            log.error("Unhandled global synchronous exception (CLIENT): {}", cause.getMessage(), cause);
        }
    }
}
