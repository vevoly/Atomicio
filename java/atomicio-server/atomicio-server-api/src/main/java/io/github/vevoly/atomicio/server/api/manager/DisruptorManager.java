package io.github.vevoly.atomicio.server.api.manager;

import io.github.vevoly.atomicio.server.api.AtomicIOEngine;

import java.util.function.Consumer;

/**
 * Disruptor 管理器接口
 *
 * @since 0.6.4
 * @author vevoly
 */
public interface DisruptorManager {

    /**
     * 启动 Disruptor 管理器
     * @param engine
     */
    void start(AtomicIOEngine engine);

    /**
     * 关闭 Disruptor 管理器
     */
    void shutdown();

    /**
     * 发布一个事件到 Disruptor 队列中
     * @param entryPreparer
     */
    void publish(Consumer<DisruptorEntry> entryPreparer);

    /**
     * 获取缓冲区大小
     * @return
     */
    long getBufferSize();

    /**
     * 获取剩余容量
     * @return
     */
    long getRemainingCapacity();

}
