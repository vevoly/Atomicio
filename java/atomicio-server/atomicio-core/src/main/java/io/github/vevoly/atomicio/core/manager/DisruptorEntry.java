package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.api.constants.IdleState;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import lombok.Data;

/**
 * 在 Disruptor 队列中传递的事件对象。
 * 它封装了处理一个事件所需的所有上下文信息。一个 Entry 就是 Disruptor 队列中的一个蹲坑。
 *
 * @since 0.0.3
 * @author vevoly
 */
@Data
public class DisruptorEntry {
    private AtomicIOEventType type;
    private AtomicIOSession session;
    private AtomicIOMessage message;
    private Throwable cause;
    private AtomicIOClusterMessage clusterMessage;
    private IdleState idleState;

    /**
     * 用于在处理完事件后清理对象，以便 Disruptor 复用。
     */
    public void clear() {
        this.type = null;
        this.session = null;
        this.message = null;
        this.cause = null;
        this.clusterMessage = null;
        this.idleState = null;
    }
}
