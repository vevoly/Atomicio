package io.github.vevoly.atomicio.api.listeners;

/**
 * AtomicIO 事件监听器接口。
 *
 * @since 0.0.1
 * @author vevoly
 */
@FunctionalInterface
public interface AtomicIOEventListener<T> {

    /**
     * 处理事件。
     * @param eventData 事件相关的数据
     */
    void onEvent(T eventData);
}
