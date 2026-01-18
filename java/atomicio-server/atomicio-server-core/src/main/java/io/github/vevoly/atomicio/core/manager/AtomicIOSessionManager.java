package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.server.api.manager.SessionManager;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 会话管理器
 * 物理连接管理
 *
 * @since 0.5.8
 * @author vevoly
 */
@Slf4j
public class AtomicIOSessionManager implements SessionManager {

    // 核心物理连接：SessionId -> Session 对象
    private final Map<String, AtomicIOSession> allSessions = new ConcurrentHashMap<>();

    // 索引：UserId -> Set<SessionId> (用于本地单人多端推送)
    private final Map<String, Set<String>> userToSessionIds = new ConcurrentHashMap<>();
    // 索引：DeviceId -> SessionId (用于处理 StateManager 返回的物理踢人)
    private final Map<String, String> deviceToSessionId = new ConcurrentHashMap<>();

    /**
     * 纯粹的本地添加，不涉及任何登录策略决策
     */
    @Override
    public void addLocalSession(AtomicIOSession session) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        String deviceId = session.getAttribute(AtomicIOSessionAttributes.DEVICE_ID);
        String sessionId = session.getId();
        allSessions.put(session.getId(), session);
        if (userId != null) {
            userToSessionIds.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session.getId());
        }
        if (deviceId != null) {
            deviceToSessionId.put(deviceId, sessionId);
        }
        log.debug("SessionManager: 物理连接已入库 [User: {}, Device: {}, Session: {}]", userId, deviceId, sessionId);
    }

    /**
     * 物理踢人：根据 DeviceId 移除并关闭本地连接
     * 由 Engine 在收到 StateManager 的 register 结果后调用
     */
    @Override
    public void removeByDeviceId(String deviceId) {
        String sessionId = deviceToSessionId.remove(deviceId);
        if (sessionId != null) {
            removeLocalSession(sessionId);
        }
    }

    /**
     * 仅获取本节点的物理连接
     */
    @Override
    public List<AtomicIOSession> getLocalSessionsByUserId(String userId) {
        Set<String> sessionIds = userToSessionIds.get(userId);
        if (sessionIds == null) return Collections.emptyList();

        return sessionIds.stream()
                .map(allSessions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 根据会话 ID 获取 Session
     * @param sessionId
     * @return
     */
    @Override
    public AtomicIOSession getLocalSessionById(String sessionId) {
        return allSessions.get(sessionId);
    }

    /**
     * 纯粹的物理断开
     */
    @Override
    public void removeLocalSession(String sessionId) {
        AtomicIOSession session = allSessions.remove(sessionId);
        if (session == null) {
            return;
        }

        // 清理索引
        cleanIndexes(session);

        // 执行物理关闭
        if (session.isActive()) {
            session.close();
        }
        log.info("SessionManager: 物理连接已断开 [Session: {}]", sessionId);
    }

    /**
     * 仅清理索引，不执行 session.close()
     */
    @Override
    public void removeLocalSessionOnly(String sessionId) {
        AtomicIOSession session = allSessions.remove(sessionId);
        if (session != null) {
            // 仅清理索引，不执行 session.close()
            cleanIndexes(session);
        }
    }

    /**
     * 本地推送逻辑：纯粹的物理遍历发送
     */
    @Override
    public boolean sendToUserLocally(String userId, Object message) {
        List<AtomicIOSession> locals = getLocalSessionsByUserId(userId);
        if (locals.isEmpty()) return false;

        locals.forEach(session -> {
            if (session.isActive()) {
                session.send(message);
            }
        });
        return true;
    }

    /**
     * 全局本地广播：不涉及集群逻辑，仅负责本物理机发送
     */
    @Override
    public void broadcastLocally(Object message) {
        allSessions.values().forEach(session -> {
            if (session.isActive()) {
                session.send(message);
            }
        });
    }

    /**
     * 获取当前节点的连接数
     * @return
     */
    @Override
    public int getTotalConnectCount() {
        return allSessions.size(); // 直接返回本地物理 Map 的大小
    }

    /**
     * 清理索引
     * @param session
     */
    private void cleanIndexes(AtomicIOSession session) {
        // 清理 UserId 索引
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        if (userId != null) {
            Set<String> ids = userToSessionIds.get(userId);
            if (ids != null) {
                ids.remove(session.getId());
                if (ids.isEmpty()) userToSessionIds.remove(userId);
            }
        }
        // 清理 DeviceId 索引
        String deviceId = session.getAttribute(AtomicIOSessionAttributes.DEVICE_ID);
        if (deviceId != null) {
            deviceToSessionId.remove(deviceId);
        }
    }
}
