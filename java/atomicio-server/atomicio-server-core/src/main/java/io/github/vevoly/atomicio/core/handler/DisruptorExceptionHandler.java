package io.github.vevoly.atomicio.core.handler;

import com.lmax.disruptor.ExceptionHandler;
import io.github.vevoly.atomicio.common.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.manager.DisruptorEntry;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Disruptor 的自定义全局异常处理器
 * 它负责捕获所有在事件消费者（如 DisruptorEventHandler）中抛出的未处理异常。
 * 它的核心职责是将这些异步异常与原始的会话（Session）关联起来，并执行统一的清理和通知逻辑。
 *
 * @since 0.6.8
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
public class DisruptorExceptionHandler implements ExceptionHandler<DisruptorEntry> {

    /**
     * 当处理 Disruptor 事件的过程中发生异常时被调用。
     * 这是我们处理业务逻辑异步异常的核心入口。
     */
    @Override
    public void handleEventException(Throwable ex, long sequence, DisruptorEntry event) {
        // 记录详细的错误日志
        String sessionId = (event != null && event.getSession() != null) ? event.getSession().getId() : "N/A";
        log.error("Disruptor 捕获到一个不能处理到异常 for session [{}] while processing event. Sequence: {}. Event 详情: {}",
                sessionId, sequence, event, ex);
        // 尝试通知客户端并关闭连接
        if (event != null && event.getSession() != null) {
            AtomicIOSession session = event.getSession();
            if (session.isActive()) {
                try {
                    AtomicIOMessage errorMessage = session.getEngine().getCodecProvider()
                            .createResponse(null, AtomicIOCommand.SYSTEM_ERROR_NOTIFY, false, AtomicIOConstant.DISRUPTOR_INTERNAL_SERVER_ERROR);
                    session.sendAndClose(errorMessage);
                    log.info("Sent error notification and closed session [{}] due to Disruptor exception.", sessionId);
                } catch (Exception e) {
                    log.error("CRITICAL: Failed to send error notification while handling another exception for session [{}]. Closing connection immediately.",
                            sessionId, e);
                    session.close();
                }
            }
        }
    }

    /**
     * 当 Disruptor 启动时发生异常时调用。
     * 这是一个致命错误，通常意味着配置问题，应该中止应用。
     */
    @Override
    public void handleOnStartException(Throwable throwable) {
        log.error("FATAL: Exception during Disruptor startup. The application will be unstable.", throwable);
    }

    /**
     * 当 Disruptor 关闭时发生异常时调用。
     */
    @Override
    public void handleOnShutdownException(Throwable throwable) {
        log.error("Exception during Disruptor shutdown.", throwable);
    }
}
