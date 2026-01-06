package io.github.vevoly.atomicio.core.event;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.api.constants.IdleState;
import io.github.vevoly.atomicio.core.engine.AtomicIOEventHandler;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.api.AtomicIOMessage;

/**
 * 封装 Disruptor 的初始化、关闭和事件发布。
 *
 * @since 0.0.3
 * @author vevoly
 */
public class DisruptorManager {

    private Disruptor<AtomicIOEvent> disruptor;
    private RingBuffer<AtomicIOEvent> ringBuffer;

    public void start(DefaultAtomicIOEngine engine) {
        // 创建 Disruptor 实例
        disruptor = new Disruptor<>(
                AtomicIOEvent::new,          // Event 工厂
                1024 * 16,                   // RingBuffer 大小
                DaemonThreadFactory.INSTANCE // 线程工厂
        );
        // 连接消费者
        disruptor.handleEventsWith(new AtomicIOEventHandler(engine));
        // 启动 Disruptor
        this.ringBuffer = disruptor.start();
    }

    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
        }
    }

    /**
     * 发布事件到 RingBuffer。
     * 这个方法会被 Netty 的 I/O 线程频繁调用。
     */
    public void publishEvent(AtomicIOEventType type, AtomicIOSession session, AtomicIOMessage message, Throwable cause) {
        if (ringBuffer == null) {
            return; // 在引擎完全启动前，不应该有事件
        }
        // 从 RingBuffer 获取下一个可用的序列
        long sequence = ringBuffer.next();
        try {
            // 获取该序列上的 Event 对象
            AtomicIOEvent event = ringBuffer.get(sequence);
            event.setType(type);
            event.setSession(session);
            event.setMessage(message);
            event.setCause(cause);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 发布空闲事件
     * @param session
     * @param state
     */
    public void publishIdleEvent(AtomicIOSession session, IdleState state) {
        if (ringBuffer == null) return;
        long sequence = ringBuffer.next();
        try {
            AtomicIOEvent event = ringBuffer.get(sequence);
            event.setType(AtomicIOEventType.IDLE); // 设置事件类型
            event.setSession(session);
            event.setIdleState(state);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 发布集群事件到 RingBuffer。
     * 这个方法会被订阅线程调用。
     * @param clusterMessage  收到的集群消息
     */
    public void publishClusterEvent(AtomicIOClusterMessage clusterMessage) {
        if (ringBuffer == null) {
            return;
        }
        long sequence = ringBuffer.next();
        try {
            AtomicIOEvent event = ringBuffer.get(sequence);
            event.setClusterMessage(clusterMessage);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
