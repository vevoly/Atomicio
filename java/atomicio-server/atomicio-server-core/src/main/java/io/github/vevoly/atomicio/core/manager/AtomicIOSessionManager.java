package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.core.message.RawBytesMessage;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.manager.SessionManager;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

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
     * (HandlerAdded 时调用)
     */
    @Override
    public void addLocalSession(AtomicIOSession session) {
        allSessions.put(session.getId(), session);
        log.debug("SessionManager: 物理连接已创建 [Session: {}]", session.getId());
    }

    /**
     * 业务身份绑定 (Engine.bindUser 时调用)
     * 作用：补全 UserId 和 DeviceId 的索引
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param deviceId  设备 ID
     */
    @Override
    public void bindLocalSession(String sessionId, String userId, String deviceId) {
        AtomicIOSession session = allSessions.get(sessionId);
        if (session == null) {
            log.warn("尝试绑定一个不存在的 session: {}", sessionId);
            return;
        }
        session.setAttribute(AtomicIOSessionAttributes.USER_ID, userId);
        session.setAttribute(AtomicIOSessionAttributes.DEVICE_ID, deviceId);
        session.setAttribute(AtomicIOSessionAttributes.IS_AUTHENTICATED, true);
        // 建立 UserId 索引
        userToSessionIds.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        // 建立 DeviceId 索引
        if (deviceId != null) {
            deviceToSessionId.put(deviceId, sessionId);
        }
        log.info("SessionManager: 身份绑定成功 [User: {}, Device: {}, Session: {}]", userId, deviceId, sessionId);
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

    @Override
    public AtomicIOSession getLocalSessionByDeviceId(String deviceId) {
        String sessionId = deviceToSessionId.get(deviceId);
        if (sessionId == null) return null;
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

    @Override
    public void kickOutLocally(String userId, @Nullable AtomicIOMessage kickOutMessage) {
        List<AtomicIOSession> locals = getLocalSessionsByUserId(userId);
        if (locals.isEmpty()) return;

        for (AtomicIOSession session : locals) {
            if (!session.isActive()) continue;
            if (kickOutMessage != null) {
                session.sendAndClose(kickOutMessage);
            } else {
                session.close();
            }
        }
    }

    @Override
    public void kickOutByDeviceId(String deviceId, AtomicIOMessage kickOutMessage) {
        AtomicIOSession session = getLocalSessionByDeviceId(deviceId);
        if (session == null || !session.isActive()) return;

        log.info("Kicking out session by deviceId: [{}].", deviceId);
        if (kickOutMessage != null) {
            session.sendAndClose(kickOutMessage);
        } else {
            session.close();
        }
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
