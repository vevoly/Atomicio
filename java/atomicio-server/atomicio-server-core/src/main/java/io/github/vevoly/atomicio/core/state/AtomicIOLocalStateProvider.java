package io.github.vevoly.atomicio.core.state;

import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.state.AtomicIOGroupStateProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOSessionStateProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地内存的 StateProvider 实现。
 * 用于单机模式。所有操作都是同步的，并立即返回一个已完成的 Future。
 */
@Slf4j
public class AtomicIOLocalStateProvider implements AtomicIOStateProvider, AtomicIOSessionStateProvider, AtomicIOGroupStateProvider {

    // 会话状态：userId -> Map<deviceId, nodeId>
    private final Map<String, Map<String, String>> userSessions = new ConcurrentHashMap<>();

    // 群组状态：groupId -> Set<userId>
    private final Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();
    // 用户加入的群组（反向索引）：userId -> Set<groupId>
    private final Map<String, Set<String>> userGroups = new ConcurrentHashMap<>();

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

    // --- SessionStateProvider 实现 ---
    @Override
    public CompletableFuture<Map<String, String>> register(AtomicIOBindRequest request, String nodeId, boolean isMultiLogin) {
        String userId = request.getUserId();
        String deviceId = request.getDeviceId();

        Map<String, String> kickedMap = new HashMap<>();

        userSessions.compute(userId, (k, sessions) -> {
            if (sessions == null) {
                sessions = new ConcurrentHashMap<>();
            }
            if (!isMultiLogin) {
                // 单点登录模式：记录所有旧会话并清空
                kickedMap.putAll(sessions);
                sessions.clear();
            }
            sessions.put(deviceId, nodeId);
            return sessions;
        });
        return CompletableFuture.completedFuture(kickedMap);
    }

    @Override
    public CompletableFuture<Void> unregister(String userId, String deviceId) {
        Map<String, String> devices = userSessions.get(userId);
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
        Map<String, String> removed = userSessions.remove(userId);
        return CompletableFuture.completedFuture(removed != null ? removed : Collections.emptyMap());
    }

    @Override
    public CompletableFuture<Map<String, String>> findSessions(String userId) {
        Map<String, String> sessions = userSessions.getOrDefault(userId, Collections.emptyMap());
        return CompletableFuture.completedFuture(sessions);
    }

    @Override
    public CompletableFuture<Boolean> isUserOnline(String userId) {
        Map<String, String> sessions = userSessions.get(userId);
        return CompletableFuture.completedFuture(sessions != null && !sessions.isEmpty());
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
