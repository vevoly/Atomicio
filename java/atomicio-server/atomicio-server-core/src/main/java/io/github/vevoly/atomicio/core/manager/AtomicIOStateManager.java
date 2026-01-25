package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.StateManager;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 状态管理器
 * 协调状态存储 (StateProvider) 与集群通信 (ClusterManager)
 *
 * @since 0.6.4
 * @author vevoly
 */
@Slf4j
public class AtomicIOStateManager implements StateManager {

    private final AtomicIOStateProvider stateProvider;
    private final ClusterManager clusterManager;
    private final String currentNodeId;

    public AtomicIOStateManager(AtomicIOStateProvider stateProvider, @Nullable ClusterManager clusterManager) {
        this.stateProvider = stateProvider;
        this.clusterManager = clusterManager;
        this.currentNodeId = (clusterManager != null)
                ? clusterManager.getCurrentNodeId()
                : AtomicIOConfigDefaultValue.SYS_ID;
    }

    @Override
    public void start() {
        if (stateProvider != null) {
            stateProvider.start();
        }
    }

    @Override
    public void shutdown() {
        if (stateProvider != null) {
            stateProvider.shutdown();
        }
    }

    // =====================================================================
    //  会话管理 (Session Management)
    // =====================================================================

    @Override
    public CompletableFuture<Map<String, String>> register(AtomicIOBindRequest request, boolean isMultiLogin) {
        // 调用 Provider 更新全局状态
        return stateProvider.getSessionStateProvider()
                .register(request, currentNodeId, isMultiLogin);
//                .thenApply(kickedMap -> {
//                    // 集群通知逻辑
//                    if (clusterManager != null && !kickedMap.isEmpty()) {
//                        // 将被踢的用户按目标节点分组
//                        Map<String, List<String>> nodeToDevices = kickedMap.entrySet().stream()
//                                .filter(entry -> !currentNodeId.equals(entry.getValue())) // 只处理远程节点
//                                .collect(Collectors.groupingBy(
//                                        Map.Entry::getValue,
//                                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
//                                ));
//
//                        // 为每个远程节点发送一条批量踢人指令
//                        nodeToDevices.forEach((targetNodeId, deviceIds) -> {
//                            log.info("StateManager: 向节点 {} 发送批量踢人指令 [User:{}, Devices:{}]",
//                                    targetNodeId, request.getUserId(), deviceIds);
//                            AtomicIOClusterMessage msg = buildKickOutMessage(request.getUserId(), deviceIds);
//                            clusterManager.publishToNode(targetNodeId, msg);
//                        });
//                    }
//                    return kickedMap;
//                });
    }

    @Override
    public CompletableFuture<Void> unregister(String userId, String deviceId) {
        return stateProvider.getSessionStateProvider().unregister(userId, deviceId);
    }

    /**
     * 强制下线用户：逻辑编排
     * 1. 从存储中移除 2. 发布集群通知
     */
    @Override
    public CompletableFuture<Map<String, String>> kickUserGlobal(String userId) {
        // 调用 Provider 原子性地删除该用户的所有会话记录
        return stateProvider.getSessionStateProvider().unregisterAll(userId)
                .thenApply(kickedSessions -> {
                    log.info("StateManager: User '{}' global sessions cleared. Count: {}",
                            userId, (kickedSessions != null ? kickedSessions.size() : 0));
                    return kickedSessions; // 返回给 Engine
                });

//                .thenAccept(kickedSessions -> { // kickedSessions: Map<deviceId, nodeId>
//                    // 如果是集群模式，且确实有会话被踢掉，则发布集群通知
//                    if (clusterManager != null && kickedSessions != null && !kickedSessions.isEmpty()) {
//                        log.debug("User '{}' was globally kicked. Kicked sessions: {}", userId, kickedSessions);
//                        // 将被踢的会话按目标节点进行分组
//                        Map<String, List<String>> nodeToDevices = kickedSessions.entrySet().stream()
//                                // 排除在本节点上的会话，因为它们将由 Engine 直接处理
//                                .filter(entry -> !currentNodeId.equals(entry.getValue()))
//                                .collect(Collectors.groupingBy(
//                                        Map.Entry::getValue, // 按 nodeId 分组
//                                        Collectors.mapping(Map.Entry::getKey, Collectors.toList()) // 收集 deviceId
//                                ));
//                        // 为每个远程节点，发送一条批量踢人指令
//                        nodeToDevices.forEach((targetNodeId, deviceIdsOnNode) -> {
//                            log.info("StateManager: 向节点 {} 发送全局踢人指令 [User:{}, Devices:{}]",
//                                    targetNodeId, userId, deviceIdsOnNode);
//                            // 创建批量踢人消息
//                            AtomicIOClusterMessage kickOutMessage = buildKickOutMessage(userId, deviceIdsOnNode);
//                            // 通过 ClusterManager 定向发布
//                            clusterManager.publishToNode(targetNodeId, kickOutMessage);
//                        });
//                    }
//                });
    }

    // =====================================================================
    //  群组管理 (Group Management)
    // =====================================================================

    @Override
    public CompletableFuture<Void> joinGroup(String groupId, String userId) {
        // 如果加入群组需要广播通知，在这里添加 clusterManager.publish(...)
        return stateProvider.getGroupStateProvider().join(groupId, userId);
    }

    @Override
    public CompletableFuture<Void> leaveGroup(String groupId, String userId) {
        return stateProvider.getGroupStateProvider().leave(groupId, userId);
    }

    @Override
    public CompletableFuture<Set<String>> getGroupMembers(String groupId) {
        return stateProvider.getGroupStateProvider().getGroupMembers(groupId);
    }

    @Override
    public CompletableFuture<String> findNodeForUser(String userId) {
        return stateProvider.getSessionStateProvider().findNodeForUser(userId);
    }

    @Override
    public CompletableFuture<Map<String, String>> findNodesForUsers(List<String> userIds) {
        return stateProvider.getSessionStateProvider().findNodesForUsers(userIds);
    }

    /**
     * 构建挤下线通知消息
     */
    /**
     * 创建一个（批量）踢人指令的集群消息。
     */
    private AtomicIOClusterMessage buildKickOutMessage(String userId, List<String> deviceIds) {
        AtomicIOClusterMessage msg = new AtomicIOClusterMessage();
        msg.setMessageType(AtomicIOClusterMessageType.KICK_OUT);
        msg.setTargetUserId(userId);
        msg.setTargetDeviceIds(deviceIds);
        msg.setFromNodeId(clusterManager.getCurrentNodeId());
        return msg;
    }

}
