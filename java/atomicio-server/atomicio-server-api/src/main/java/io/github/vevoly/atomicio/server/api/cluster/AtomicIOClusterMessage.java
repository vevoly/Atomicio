package io.github.vevoly.atomicio.server.api.cluster;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
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
     */
    private AtomicIOClusterMessageType messageType;

    /**
     * 目标
     */
    private String target; // userId, groupId, all

    /**
     * 排除的用户
     */
    private Set<String> excludeUserIds;

    /**
     * 目标设备 ID
     * 专门用于踢出指定设备，若为空则表示踢出该用户所有设备
     */
    private String deviceId;

    /**
     * 节点 ID (可选)
     * 标明这条消息是谁发出的
     */
    private String fromNodeId;

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
