package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.common.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.common.api.exception.AtomicIOExceptionHandler;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务器端同步异常处理器
 *
 * @since 0.6.8
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultExceptionHandler implements AtomicIOExceptionHandler {

    private final AtomicIOEngine engine;

    @Override
    public void handle(Object context, Throwable cause) {
        if (context instanceof ChannelHandlerContext ctx) {
            // 如果 Channel 已经不活跃，就没必要再尝试发送消息了
            if (!ctx.channel().isActive()) {
                log.warn("捕获的异常在非活跃 Channel [{}]. 忽略.", ctx.channel().id());
                return;
            }
            log.error("服务器 Pipeline 捕获到未处理异常 [Channel ID: {}]: {}",
                    ctx.channel().id(), cause.getMessage(), cause);
            try {
                // 1. 获取与此 Channel 关联的 CodecProvider
                AtomicIOSession session = engine.getSessionManager().getLocalSessionById(ctx.channel().id().asLongText());
                if (session == null) {
                    // 如果连 session 都没有，说明异常发生在非常早的阶段，直接关闭
                    ctx.close();
                    return;
                }
                AtomicIOServerCodecProvider codecProvider = engine.getCodecProvider();

                // 2. 准备错误信息
                String errorMessageText = AtomicIOConstant.INTERNAL_SERVER_ERROR + ":" + cause.getMessage();

                // 3. 使用 CodecProvider 创建协议无关的错误响应消息
                AtomicIOMessage errorMessage = codecProvider.createResponse(
                        null, // 没有原始请求
                        AtomicIOCommand.SYSTEM_ERROR_NOTIFY,
                        false,
                        errorMessageText
                );

                // ★ 4. 发送消息后关闭连接
                ctx.writeAndFlush(errorMessage).addListener(future -> {
                    if (!future.isSuccess()) {
                        log.warn("发送错误通知到 channel [{}] 失败: {}", ctx.channel().id(), future.cause().getMessage());
                    }
                    ctx.close();
                });

            } catch (Exception e) {
                // 如果在创建或发送错误消息的过程中又发生了异常，这说明系统处于非常不稳定的状态，唯一的安全操作就是直接关闭连接。
                log.error("CRITICAL: Exception occurred while handling another exception for channel [{}]. Closing connection immediately.",
                        ctx.channel().id(), e);
                ctx.close();
            }

        } else {
            // 处理非 Pipeline 的异常
            log.error("捕获到全局同步异常 (非网络链路): {}", cause.getMessage(), cause);
        }
    }
}
