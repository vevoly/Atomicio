package io.github.vevoly.atomicio.core.manager;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.github.vevoly.atomicio.core.handler.DisruptorEventHandler;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
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

    /**
     * 开启 IO 队列
     * @param engine
     */
    public void start(DefaultAtomicIOEngine engine) {
        // todo 对 Disruptor 进行精细配置
        // 从配置中读取，如果没有，则默认给 131072
//        int bufferSize = engine.getConfig().getIoQueueSize() > 0 ?
//                findNextPositivePowerOfTwo(engine.getConfig().getIoQueueSize()) : 131072;

// 消费者数量建议设置为 CPU 核心数
//        int workerCount = engine.getConfig().getIoWorkerCount() > 0 ?
//                engine.getConfig().getIoWorkerCount() : Runtime.getRuntime().availableProcessors();
//        // 1. 获取 CPU 核心数作为参考
//        int cpuCores = Runtime.getRuntime().availableProcessors();
//        // 如果是纯计算，设为 cpuCores；如果有 IO 阻塞，可以设为 cpuCores * 2
//        int workerCount = Math.max(cpuCores, 4);
//        // 2. 创建多个消费者实例
//        DisruptorEventHandler[] workers = new DisruptorEventHandler[workerCount];
//        for (int i = 0; i < workerCount; i++) {
//            workers[i] = new DisruptorEventHandler(engine);
//        }
//        // 3. 使用 WorkerPool 模式，多个线程共同消费同一个 RingBuffer
//        // 这样每个 Event 只会被其中一个线程处理（多线程竞争消费）
//        disruptor.handleEventsWithWorkerPool(workers);

        disruptor = new Disruptor<>(
                DisruptorEntry::new,         // Event 工厂
                65536,                       // RingBuffer 大小
                DaemonThreadFactory.INSTANCE // 线程工厂
        );
        // 连接消费者
        disruptor.handleEventsWith(new DisruptorEventHandler(engine));
        this.ringBuffer = disruptor.start();
    }

    /**
     * 关闭队列
     */
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

    /**
     * 获取队列大小
     * @return
     */
    public long getBufferSize() {
        return ringBuffer != null ? ringBuffer.getBufferSize() : -1;
    }

    /**
     * 获取队列剩余容量
     * @return
     */
    public long getRemainingCapacity() {
        return ringBuffer != null ? ringBuffer.remainingCapacity() : -1;
    }
}
