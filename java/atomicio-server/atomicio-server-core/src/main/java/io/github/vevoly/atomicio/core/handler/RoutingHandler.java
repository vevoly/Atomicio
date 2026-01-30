package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.GroupManager;
import io.github.vevoly.atomicio.server.api.manager.SessionManager;
import io.github.vevoly.atomicio.server.api.manager.StateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 路由处理器
 * 封装了所有消息路由逻辑的内部处理器
 *
 * @since 0.6.10
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
public class RoutingHandler {

    private final AtomicIOEngine engine;

    private SessionManager sessionManager() { return engine.getSessionManager(); }
    private GroupManager groupManager() { return engine.getGroupManager(); }
    private StateManager stateManager() { return engine.getStateManager(); }
    private ClusterManager clusterManager() { return engine.getClusterManager(); }

    private boolean isClusterMode() { return clusterManager() != null; }

    public void sendToUser(String userId, AtomicIOMessage message) {
        sendToUsers(Collections.singletonList(userId), message);
    }

    public void sendToUsers(List<String> userIds, AtomicIOMessage message) {
        if (userIds == null || userIds.isEmpty()) return;

        // 1. 【本地投递】首先，尝试向所有在本节点的 session 投递
        List<String> notFoundLocally = userIds.stream()
                .filter(userId -> !sessionManager().sendToUserLocally(userId, message))
                .collect(Collectors.toList());

        // 2. 【集群投递】如果开启了集群模式，并且有部分用户不在本地，则启动远程路由
        if (isClusterMode() && !notFoundLocally.isEmpty()) {
            log.debug("Users not found locally: {}. Attempting cluster routing.", notFoundLocally);
            // ★ 完全委托给 ClusterManager 去处理复杂的远程路由
            clusterManager().sendToUsers(notFoundLocally, message);
            engine.getClusterManager().sendToUsers(notFoundLocally, message);
        }
    }

    public void sendToGroup(String groupId, AtomicIOMessage message, Set<String> excludeUserIds) {
        groupManager().sendToGroupLocally(groupId, message, excludeUserIds);
        if (isClusterMode()) {
            clusterManager().sendToGroup(groupId, message, excludeUserIds);
        }
    }

    public void broadcast(AtomicIOMessage message) {
        sessionManager().broadcastLocally(message);
        if (isClusterMode()) {
            clusterManager().broadcast(message);
        }
    }
}
