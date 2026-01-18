package io.github.vevoly.atomicio.server.api.cluster;

/**
 * 集群消息类型
 *
 * @since 0.0.4
 * @author vevoly
 */
public enum AtomicIOClusterMessageType {
    SEND_TO_USER,
    SEND_TO_GROUP,
    BROADCAST,
    KICK_OUT,
}
