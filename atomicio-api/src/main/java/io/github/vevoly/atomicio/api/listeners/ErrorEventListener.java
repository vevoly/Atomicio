package io.github.vevoly.atomicio.api.listeners;

import io.github.vevoly.atomicio.api.AtomicIOSession;

/**
 * 异常事件监听器
 *
 * @since 0.0.1
 * @author vevoly
 */
@FunctionalInterface
public interface ErrorEventListener {

    /**
     * 当发生异常时被调用。
     * @param session 发生异常的会话 (可能为 null)
     * @param cause   异常对象
     */
    void onEvent(AtomicIOSession session, Throwable cause);
}
