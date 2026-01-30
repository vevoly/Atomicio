package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.dto.SessionDetails;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.StateManager;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    @Getter
    private final String currentNodeId;

    public AtomicIOStateManager(AtomicIOStateProvider stateProvider, ClusterManager clusterManager) {
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
    public CompletableFuture<Void> register(AtomicIOBindRequest request) {
        return stateProvider.getSessionStateProvider()
                .register(request, currentNodeId);
    }

    @Override
    public CompletableFuture<Map<String, String>> replaceAndNotify(AtomicIOBindRequest newRequest, Set<String> devicesToKick) {
        // 1. 调用 Provider 执行原子性的替换操作
        return stateProvider.getSessionStateProvider()
                .replaceSession(newRequest, currentNodeId, devicesToKick)
                .thenApply(kickedMap -> {
                    // 2. 编排：状态更新成功后，发布集群踢人通知
                    if (clusterManager != null && !kickedMap.isEmpty()) {
                        // 按节点分组
                        Map<String, List<String>> nodeToDevices = kickedMap.entrySet().stream()
                                .collect(Collectors.groupingBy(
                                        Map.Entry::getValue,
                                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                                ));

                        // 为每个节点（无论本地还是远程）发送一条踢人指令
                        // Engine/Disruptor 会区分并处理
                        nodeToDevices.forEach((targetNodeId, deviceIds) -> {
                            log.info("StateManager: 向节点 {} 发送踢人指令 [User:{}, Devices:{}]",
                                    targetNodeId, newRequest.getUserId(), deviceIds);
                            AtomicIOClusterMessage msg = buildKickOutMessage(newRequest.getUserId(), deviceIds);
                            clusterManager.publishToNode(targetNodeId, msg);
                        });
                    }
                    return kickedMap;
                });
    }

    @Override
    public CompletableFuture<Void> unregister(String userId, String deviceId) {
        return stateProvider.getSessionStateProvider().unregister(userId, deviceId);
    }

    @Override
    public CompletableFuture<Map<String, String>> kickUserGlobalAndNotify(String userId) {
        // 1. 调用 StateProvider 执行原子性的“全部注销”操作。
        //    Provider 会返回一个 Map<deviceId, nodeId>，包含了所有被清理的会话。
        return stateProvider.getSessionStateProvider().unregisterAll(userId)
                .thenApply(kickedNodeMap -> { // 使用 thenApply 以便能将 kickedNodeMap 返回给 Engine
                    // 2. 如果是集群模式，且确实有会话被清理，则发布集群通知
                    if (clusterManager != null && kickedNodeMap != null && !kickedNodeMap.isEmpty()) {
                        log.debug("User '{}' was globally kicked. Kicked sessions: {}", userId, kickedNodeMap);
                        // 3. 将被踢的会话按目标节点进行分组
                        Map<String, List<String>> nodeToDevices = kickedNodeMap.entrySet().stream()
                                .collect(Collectors.groupingBy(
                                        Map.Entry::getValue, // 按 nodeId 分组
                                        Collectors.mapping(Map.Entry::getKey, Collectors.toList()) // 收集 deviceId
                                ));
                        // 4. 为每个节点（无论本地还是远程）发送一条批量踢人指令
                        // Disruptor 统一处理所有节点的踢人逻辑，这样保证串行执行，避免并发问题。
                        nodeToDevices.forEach((targetNodeId, deviceIdsOnNode) -> {
                            log.info("StateManager: 向节点 {} 发送全局踢人指令 [User:{}, Devices:{}]",
                                    targetNodeId, userId, deviceIdsOnNode);
                            // 创建批量踢人消息。
                            //    全局踢人通常由管理后台发起，不附带特定的“挤下线”通知消息，
                            //    所以 payload 可以为 null。
                            AtomicIOClusterMessage kickOutMessage = buildKickOutMessage(userId, deviceIdsOnNode);
                            // 通过 ClusterManager 定向发布
                            clusterManager.publishToNode(targetNodeId, kickOutMessage);
                        });
                    }
                    // 5. 将踢人结果 Map 返回，供上层 Engine 使用
                    return kickedNodeMap;
                });
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
    public CompletableFuture<Boolean> isDeviceOnline(String userId, String deviceId) {
        return stateProvider.getSessionStateProvider().isDeviceOnline(userId, deviceId);
    }

    @Override
    public CompletableFuture<Map<String, SessionDetails>> findSessionDetails(String userId) {
        return stateProvider.getSessionStateProvider().findSessionDetails(userId);
    }

    @Override
    public CompletableFuture<Map<String, SessionDetails>> findSessionDetailsByType(String userId, String deviceType) {
        return stateProvider.getSessionStateProvider().findSessionDetailsByType(userId, deviceType);
    }

    @Override
    public CompletableFuture<Set<String>> findNodesForUser(String userId) {
        return stateProvider.getSessionStateProvider().findNodesForUser(userId);
    }

    @Override
    public CompletableFuture<Map<String, Set<String>>> findNodesForUsers(List<String> userIds) {
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
        msg.setFromNodeId(this.getCurrentNodeId());
        return msg;
    }




}
