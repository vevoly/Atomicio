package io.github.vevoly.atomicio.core.cluster;

import io.github.vevoly.atomicio.api.AtomicIOMessage;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 内部消息实现
 * 用于从集群消息中重构 AtomicIOMessage。
 *
 * @since 0.4.1
 * @author vevoly
 */
@Data
@AllArgsConstructor
public class ReconstructedMessage implements AtomicIOMessage {
    private final int commandId;
    private final byte[] payload;
}
