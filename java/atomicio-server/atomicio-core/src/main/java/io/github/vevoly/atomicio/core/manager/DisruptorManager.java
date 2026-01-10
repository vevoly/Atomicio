package io.github.vevoly.atomicio.core.manager;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.api.constants.IdleState;
import io.github.vevoly.atomicio.core.handler.DisruptorEventHandler;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * 封装 Disruptor 的初始化、关闭和事件发布。
 *
 * @since 0.0.3
 * @author vevoly
 */
@Slf4j
public class DisruptorManager {

    private Disruptor<DisruptorEntry> disruptor;
    private RingBuffer<DisruptorEntry> ringBuffer;

    public void start(DefaultAtomicIOEngine engine) {
        // 创建 Disruptor 实例
        disruptor = new Disruptor<>(
                DisruptorEntry::new,         // Event 工厂
                1024 * 16,                   // RingBuffer 大小
                DaemonThreadFactory.INSTANCE // 线程工厂
        );
        // 连接消费者
        disruptor.handleEventsWith(new DisruptorEventHandler(engine));
        this.ringBuffer = disruptor.start();
    }

    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
        }
    }

    /**
     * 通用的发布方法
     * 调用者负责准备好 Entry，管理器只负责发布。
     *
     * @param entryPreparer 一个 Consumer，它会接收一个 RingBuffer 中的空 Entry，并负责填充数据。
     */
    public void publish(Consumer<DisruptorEntry> entryPreparer) {
        if (ringBuffer == null) {
            log.warn("Disruptor is not started yet, event is dropped.");
            return;
        }

        long sequence = ringBuffer.next();
        try {
            DisruptorEntry event = ringBuffer.get(sequence);
            // 将填充数据的逻辑委托给调用者
            entryPreparer.accept(event);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
