package io.github.vevoly.atomicio.common.api.exception;

/**
 * 通用异常处理器接口
 *
 * @since 0.6.8
 * @author vevoly
 */
public interface AtomicIOExceptionHandler {

    /**
     * 异常处理
     *
     * @param context 一个可选的上下文对象 (例如 Netty 的 ChannelHandlerContext)。
     * @param cause   发生的异常。
     */
    void handle(Object context, Throwable cause);
}
