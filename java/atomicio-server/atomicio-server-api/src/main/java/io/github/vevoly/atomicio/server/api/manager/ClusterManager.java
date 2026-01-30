package io.github.vevoly.atomicio.server.api.manager;

import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;

import java.util.List;
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
     * 向一个指定的节点发布集群消息
     *
     * @param targetNodeId 目标节点的唯一ID
     * @param message      要发送的集群消息POJO
     */
    void publishToNode(String targetNodeId, AtomicIOClusterMessage message);

    /**
     * 构建集群消息
     * @param message        消息
     * @param messageType    消息类型
     * @param target         发送目标
     * @param excludeUserIds 排除用户
     * @return
     */
    AtomicIOClusterMessage buildClusterMessage (AtomicIOMessage message, AtomicIOClusterMessageType messageType, Object target, Set<String> excludeUserIds);

    /**
     * 通过集群，向指定的多个远程用户发送消息。
     * 内部会处理节点查询、分组和批量精准投递。
     *
     * @param remoteUserIds 已经确认不在本机的用户ID列表
     * @param message       要发送的消息
     */
    void sendToUsers(List<String> remoteUserIds, AtomicIOMessage message);

    /**
     * 通过集群，向一个群组广播消息。
     *
     * @param groupId        群组ID
     * @param message        要发送的消息
     * @param excludeUserIds 需要排除的用户ID
     */
    void sendToGroup(String groupId, AtomicIOMessage message, Set<String> excludeUserIds);

    /**
     * 通过集群，进行全局广播。
     *
     * @param message 要发送的消息
     */
    void broadcast(AtomicIOMessage message);

}
