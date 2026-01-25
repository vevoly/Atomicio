package io.github.vevoly.atomicio.server.api.cluster;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 在集群节点间广播的消息对象。
 * 它需要是可序列化的，以便通过 Redis 等中间件传输。
 *
 * @since 0.0.4
 * @author vevoly
 */
@Data
@ToString
public class AtomicIOClusterMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     * 决定了接收节点应该如何处理这条消息
     */
    private AtomicIOClusterMessageType messageType;

    /**
     * 节点 ID (可选)
     * 标明这条消息是谁发出的
     */
    private String fromNodeId;

    /**
     * 【单目标】用户ID (用于 SEND_TO_USER)。
     */
    private String targetUserId;

    /**
     * 【多目标】用户ID列表 (用于 SEND_TO_USERS_BATCH)。
     */
    private List<String> targetUserIds;

    /**
     * 【单目标】群组ID (用于 SEND_TO_GROUP)。
     */
    private String targetGroupId;

    /**
     * 【多目标】设备ID列表 (用于 KICK_OUT)。
     */
    private List<String> targetDeviceIds;

    /**
     * 排除的用户
     */
    private Set<String> excludeUserIds;

    /**
     * 指令号
     * 与协议无关
     */
    private int CommandId;

    /**
     * 消息负载
     * 与协议无关
     */
    private byte[] payload;
}
