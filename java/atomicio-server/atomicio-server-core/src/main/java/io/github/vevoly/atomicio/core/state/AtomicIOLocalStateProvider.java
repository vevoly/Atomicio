package io.github.vevoly.atomicio.core.state;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.dto.SessionDetails;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.state.AtomicIOGroupStateProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOSessionStateProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 本地内存的 StateProvider 实现。
 * 用于单机模式。所有操作都是同步的，并立即返回一个已完成的 Future。
 */
@Slf4j
public class AtomicIOLocalStateProvider implements AtomicIOStateProvider, AtomicIOSessionStateProvider, AtomicIOGroupStateProvider {

    // 会话状态：userId -> Map<deviceId, SessionDetails>
    private final Map<String, Map<String, SessionDetails>> userSessions = new ConcurrentHashMap<>();
    // 群组状态：groupId -> Set<userId>
    private final Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();
    // 用户加入的群组（反向索引）：userId -> Set<groupId>
    private final Map<String, Set<String>> userGroups = new ConcurrentHashMap<>();

    private final String localNodeId = AtomicIOConfigDefaultValue.SYS_ID;

    @Override
    public void start() {
        log.info("正在启动 LocalStateProvider ... ");
    }

    @Override
    public void shutdown() {
        log.info("正在关闭 LocalStateProvider...");
    }

    // --- StateProvider 实现 ---
    @Override
    public AtomicIOSessionStateProvider getSessionStateProvider() {
        return this;
    }

    @Override
    public AtomicIOGroupStateProvider getGroupStateProvider() {
        return this;
    }

    @Override
    public CompletableFuture<Void> register(AtomicIOBindRequest request, String nodeId) {
        String userId = request.getUserId();
        String deviceId = request.getDeviceId();

        SessionDetails details = new SessionDetails(
                nodeId,
                request.getDeviceType(),
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        userSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(deviceId, details);
        log.debug("LocalState: Registered session for user '{}', device '{}'.", userId, deviceId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, String>> replaceSession(AtomicIOBindRequest newRequest, String newNodeId, Set<String> devicesToKick) {
        String userId = newRequest.getUserId();
        Map<String, String> kickedMap = new HashMap<>();

        userSessions.compute(userId, (k, sessions) -> {
            if (sessions == null) {
                sessions = new ConcurrentHashMap<>();
            }
            // 1. 移除所有需要被踢掉的设备
            if (devicesToKick != null) {
                for (String deviceToKick : devicesToKick) {
                    SessionDetails kickedDetails = sessions.remove(deviceToKick);
                    if (kickedDetails != null) {
                        kickedMap.put(deviceToKick, kickedDetails.getNodeId());
                    }
                }
            }
            // 2. 添加新设备
            SessionDetails newDetails = new SessionDetails(
                    newNodeId,
                    newRequest.getDeviceType(),
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
            );
            sessions.put(newRequest.getDeviceId(), newDetails);
            return sessions;
        });
        log.debug("LocalState: Replaced sessions for user '{}'. Kicked: {}. Added: {}", userId, devicesToKick, newRequest.getDeviceId());
        return CompletableFuture.completedFuture(kickedMap);
    }

    @Override
    public CompletableFuture<Void> unregister(String userId, String deviceId) {
        Map<String, SessionDetails> devices = userSessions.get(userId);
        if (devices != null) {
            devices.remove(deviceId);
            if (devices.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, String>> unregisterAll(String userId) {
        Map<String, SessionDetails> removedDetails = userSessions.remove(userId);
        if (removedDetails == null || removedDetails.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        // 2. 转换为 Map<deviceId, nodeId>
        Map<String, String> kickedNodeMap = removedDetails.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getNodeId()
                ));
        return CompletableFuture.completedFuture(kickedNodeMap);
    }

    @Override
    public CompletableFuture<Map<String, SessionDetails>> findSessionDetailsByType(String userId, String deviceType) {
        Map<String, SessionDetails> allSessions = userSessions.get(userId);
        if (allSessions == null || deviceType == null) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        // 过滤出指定设备类型的会话
        Map<String, SessionDetails> filteredSessions = allSessions.entrySet().stream()
                .filter(entry -> deviceType.equals(entry.getValue().getDeviceType()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return CompletableFuture.completedFuture(filteredSessions);
    }

    @Override
    public CompletableFuture<Map<String, SessionDetails>> findSessionDetails(String userId) {
        Map<String, SessionDetails> details = userSessions.getOrDefault(userId, Collections.emptyMap());
        return CompletableFuture.completedFuture(new HashMap<>(details));
    }

    @Override
    public CompletableFuture<Set<String>> findNodesForUser(String userId) {
        return isUserOnline(userId).thenApply(isOnline ->
                isOnline ? Collections.singleton(localNodeId) : Collections.emptySet()
        );
    }

    @Override
    public CompletableFuture<Map<String, Set<String>>>  findNodesForUsers(List<String> userIds) {
        if (userIds == null) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        Map<String, Set<String>> userNodeMap = new HashMap<>();
        for (String userId : userIds) {
            Map<String, SessionDetails> sessions = userSessions.get(userId);
            if (sessions != null && !sessions.isEmpty()) {
                // 收集该用户所有 session 所在的 nodeId
                Set<String> nodeIds = sessions.values().stream()
                        .map(SessionDetails::getNodeId)
                        .collect(Collectors.toSet());
                userNodeMap.put(userId, nodeIds);
            }
        }
        return CompletableFuture.completedFuture(userNodeMap);
    }

    @Override
    public CompletableFuture<Boolean> isUserOnline(String userId) {
        Map<String, SessionDetails> sessions = userSessions.get(userId);
        return CompletableFuture.completedFuture(sessions != null && !sessions.isEmpty());
    }

    @Override
    public CompletableFuture<Boolean> isDeviceOnline(String userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return CompletableFuture.completedFuture(false);
        }
        // 1. 获取该用户的所有设备
        Map<String, SessionDetails> devices = userSessions.get(userId);
        // 2. 检查该设备是否存在
        boolean isOnline = (devices != null && devices.containsKey(deviceId));
        log.debug("单机检查: isDeviceOnline for user '{}', device '{}'? -> {}", userId, deviceId, isOnline);
        // 3. 立即返回一个已完成的 Future
        return CompletableFuture.completedFuture(isOnline);
    }

    @Override
    public CompletableFuture<Long> getTotalUserCount() {
        return CompletableFuture.completedFuture((long) userSessions.size());
    }

    @Override
    public CompletableFuture<Long> getTotalSessionCount() {
        long count = userSessions.values().stream().mapToInt(Map::size).sum();
        return CompletableFuture.completedFuture(count);
    }

    @Override
    public void updateSessionActivity(String userId, String deviceId, long activityTime) {
        Map<String, SessionDetails> devices = userSessions.get(userId);
        if (devices != null) {
            SessionDetails details = devices.get(deviceId);
            if (details != null) {
                details.setLastActivityTime(activityTime);
            }
        }
    }

    // --- GroupStateProvider 实现 ---
    @Override
    public CompletableFuture<Void> join(String groupId, String userId) {
        // 更新群组成员
        groupMembers.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        // 更新反向索引：用户加入了哪些群
        userGroups.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(groupId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> leave(String groupId, String userId) {
        Set<String> members = groupMembers.get(groupId);
        if (members != null) {
            members.remove(userId);
            if (members.isEmpty()) {
                groupMembers.remove(groupId);
            }
        }
        // 更新反向索引
        Set<String> groups = userGroups.get(userId);
        if (groups != null) {
            groups.remove(groupId);
            if (groups.isEmpty()) userGroups.remove(userId);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Set<String>> getGroupMembers(String groupId) {
        Set<String> members = groupMembers.getOrDefault(groupId, Collections.emptySet());
        return CompletableFuture.completedFuture(members);
    }

    @Override
    public CompletableFuture<Long> getGroupMemberCount(String groupId) {
        Set<String> members = groupMembers.get(groupId);
        return CompletableFuture.completedFuture(members != null ? (long) members.size() : 0L);
    }

    @Override
    public CompletableFuture<Boolean> isGroupMember(String groupId, String userId) {
        Set<String> members = groupMembers.get(groupId);
        return CompletableFuture.completedFuture(members != null && members.contains(userId));
    }

    @Override
    public CompletableFuture<Set<String>> getGroupsForUser(String userId) {
        return CompletableFuture.completedFuture(userGroups.getOrDefault(userId, Collections.emptySet()));
    }

    @Override
    public CompletableFuture<Long> getGroupCountForUser(String userId) {
        Set<String> groups = userGroups.get(userId);
        return CompletableFuture.completedFuture(groups != null ? (long) groups.size() : 0L);
    }
}
