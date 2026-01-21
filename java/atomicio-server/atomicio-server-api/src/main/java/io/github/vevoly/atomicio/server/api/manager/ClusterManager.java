package io.github.vevoly.atomicio.server.api.manager;

import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;

import java.util.Set;

/**
 * 集群管理器接口
 *
 * @since 0.6.4
 * @author vevoly
 */
public interface ClusterManager {

    /**
     * 启动集群管理器
     */
    void start();

    /**
     * 关闭集群管理器
     */
    void shutdown();

    /**
     * 获取当前节点 ID
     * @return
     */
    String getCurrentNodeId();

    /**
     * 发布一个消息到集群中
     * @param message
     */
    void publish(AtomicIOClusterMessage message);

    /**
     * 发布一个消息到集群中，并指定目标节点
     * @param nodeId
     * @param message
     */
    void publishKickOut(String nodeId, AtomicIOClusterMessage message);

    /**
     * 构建集群消息
     * @param message        消息
     * @param messageType    消息类型
     * @param target         发送目标
     * @param excludeUserIds 排除用户
     * @return
     */
    AtomicIOClusterMessage buildClusterMessage (AtomicIOMessage message, AtomicIOClusterMessageType messageType, String target, Set<String> excludeUserIds);

}
