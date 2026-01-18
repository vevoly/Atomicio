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

    /**
     * 注册会话：协调存储更新与集群踢人消息发布
     */
    @Override
    public CompletableFuture<Map<String, String>> register(AtomicIOBindRequest request, boolean isMultiLogin) {
        String userId = request.getUserId();

        // 1. 调用 Provider 更新全局状态
        return stateProvider.getSessionStateProvider()
                .register(request, currentNodeId, isMultiLogin)
                .thenApply(kickedMap -> {
                    // 2. 如果是集群模式，编排跨节点踢人通知
                    if (clusterManager != null && !kickedMap.isEmpty()) {
                        kickedMap.forEach((deviceId, targetNodeId) -> {
                            // 仅通知远端节点，本地由 Engine 自己通过 SessionManager 处理
                            if (!currentNodeId.equals(targetNodeId)) {
                                sendRemoteKickOut(userId, deviceId, targetNodeId);
                            }
                        });
                    }
                    return kickedMap; // 返回给 Engine，以便处理本地关闭
                });
    }

    /**
     * 注销会话：更新存储状态
     */
    @Override
    public CompletableFuture<Void> unregister(String userId, String deviceId) {
        return stateProvider.getSessionStateProvider().unregister(userId, deviceId);
    }

    /**
     * 强制下线用户：逻辑编排
     * 1. 从存储中移除 2. 发布集群通知
     */
    @Override
    public CompletableFuture<Void> kickUserGlobal(String userId) {
        return stateProvider.getSessionStateProvider().unregisterAll(userId)
                .thenAccept(allSessions -> {
                    if (clusterManager != null && !allSessions.isEmpty()) {
                        allSessions.forEach((deviceId, targetNodeId) -> {
                            if (!currentNodeId.equals(targetNodeId)) {
                                sendRemoteKickOut(userId, deviceId, targetNodeId);
                            }
                        });
                    }
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

    // =====================================================================
    //  内部辅助 (Internal Helpers)
    // =====================================================================

    private void sendRemoteKickOut(String userId, String deviceId, String targetNodeId) {
        if (clusterManager == null) return;

        AtomicIOClusterMessage msg = new AtomicIOClusterMessage();
        msg.setMessageType(AtomicIOClusterMessageType.KICK_OUT);
        msg.setTarget(userId);
        msg.setDeviceId(deviceId);
        msg.setFromNodeId(currentNodeId);

        log.info("StateManager: 向节点 {} 发送踢人指令 [User:{}, Device:{}]", targetNodeId, userId, deviceId);
        clusterManager.publishKickOut(targetNodeId, msg);
    }
}
