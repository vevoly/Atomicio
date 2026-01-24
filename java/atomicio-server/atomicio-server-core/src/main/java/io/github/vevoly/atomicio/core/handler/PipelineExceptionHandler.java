package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.common.api.exception.AtomicIOExceptionHandler;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务器端全局 Pipeline 异常处理器
 *
 * @since 0.6.8
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class PipelineExceptionHandler extends ChannelDuplexHandler {

    private final AtomicIOExceptionHandler exceptionHandler;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 当 Netty 捕获到入站异常时...
        log.info("PipelineExceptionHandler 捕获异常");
        exceptionHandler.handle(ctx, cause);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // 当处理出站消息时...
        ctx.write(msg, promise.addListener(future -> {
            if (!future.isSuccess()) {
                // 如果写入失败，这也是一个异常，委托给通用处理器
                exceptionHandler.handle(ctx, future.cause());
            }
        }));
    }
}
