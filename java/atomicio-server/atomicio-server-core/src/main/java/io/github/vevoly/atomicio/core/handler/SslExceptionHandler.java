package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.server.api.constants.ConnectionRejectType;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;

/**
 * 一个专门用于捕获并处理 SSL/TLS 握手异常的 Handler。
 *
 * @since 0.5.7
 * @author vevoly
 */
@Slf4j
@AllArgsConstructor
@ChannelHandler.Sharable
public class SslExceptionHandler extends ChannelInboundHandlerAdapter {

    private final DefaultAtomicIOEngine engine;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Throwable rootCause = findRootCause(cause);
        if (rootCause instanceof SSLException) {
            log.warn("SSL handshake failed from remote address [{}]: {}",
                    ctx.channel().remoteAddress(), rootCause.getMessage());
            engine.getEventManager().fireConnectionRejectEvent(ctx.channel(), ConnectionRejectType.SSL_HANDSHAKE_FAILED, rootCause);
            ctx.close();
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

    /**
     * 查找根原因
     * 因为 Netty 常常会把原始异常包装在 DecoderException 中
     * @param cause
     * @return
     */
    private Throwable findRootCause(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
