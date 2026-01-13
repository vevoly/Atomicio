package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.common.api.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.common.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话管理器
 *
 * @since 0.5.8
 * @author vevoly
 */
@Slf4j
public class AtomicIOSessionManager {

    private final DefaultAtomicIOEngine engine;
    private final AtomicIOProperties config;

    // 映射表
    private final Map<String, AtomicIOSession> userIdToSessionMap = new ConcurrentHashMap<>(); // 单点登录时使用
    private final Map<String, CopyOnWriteArrayList<AtomicIOSession>> userIdToSessionsMap = new ConcurrentHashMap<>(); // 多点登录时使用
    private final Map<String, String> sessionIdToUserIdMap = new ConcurrentHashMap<>(); // Key: 会话ID, Value: 对应的用户ID

    public AtomicIOSessionManager(DefaultAtomicIOEngine engine) {
        this.engine = engine;
        this.config = engine.getConfig();
    }

    /**
     * 获取当前管理的总会话数
     */
    public int getTotalConnectCount() {
        if (config.getSession().isMultiLogin()) {
            return (int) userIdToSessionsMap.values().stream().mapToLong(List::size).sum();
        } else {
            return userIdToSessionMap.size();
        }
    }

    public void bindUser(AtomicIOBindRequest request, AtomicIOSession newSession) {
        String userId = request.getUserId();
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(newSession, "Session cannot be null");

        // 1. 维护反向映射并处理 session 换绑
        String oldUserId = sessionIdToUserIdMap.put(newSession.getId(), userId);
        if (oldUserId != null && !oldUserId.equals(userId)) {
            // 这个 Session 之前绑定了另一个用户，现在要换绑
            log.warn("Session {} is being re-bound from old user '{}' to new user '{}'.",
                    newSession.getId(), oldUserId, userId);
            // 从旧用户 session 列表中移除当前 session
            if (config.getSession().isMultiLogin()) {
                CopyOnWriteArrayList<AtomicIOSession> sessions = userIdToSessionsMap.get(oldUserId);
                if (sessions != null) {
                    sessions.remove(newSession);
                }
            } else {
                userIdToSessionMap.remove(oldUserId, newSession);
            }
        }
        // 2. 根据配置决定登录模式
        if (config.getSession().isMultiLogin()) {
            CopyOnWriteArrayList<AtomicIOSession> sessions = userIdToSessionsMap.computeIfAbsent(
                    userId, k -> new CopyOnWriteArrayList<>()
            );
            sessions.add(newSession);
            log.info("User {} bound to a new session {} (Multi-session mode). Total sessions for user: {}.",
                    userId, newSession.getId(), sessions.size());
        } else {
            // 单点登录逻辑 (踢掉旧连接)
            AtomicIOSession oldSession = userIdToSessionMap.put(userId, newSession);
            if (oldSession != null && !oldSession.getId().equals(newSession.getId())) {
                log.warn("User {} re-logged in. replaced old session {}.", userId, oldSession.getId());
                engine.getEventManager().fireSessionReplacedEvent(oldSession, newSession);
            }
        }
        // 3. 在 Session 属性中存储上下文
        newSession.setAttribute(AtomicIOSessionAttributes.USER_ID, userId);
        if (request.getDeviceId() != null) {
            newSession.setAttribute(AtomicIOSessionAttributes.DEVICE_ID, request.getDeviceId());
        }
        newSession.setAttribute(AtomicIOSessionAttributes.IS_AUTHENTICATED, true);
        log.info("User {} successfully bound to session {}.", userId, newSession.getId());
    }

    public void unbindUserInternal(AtomicIOSession session) {
        // 优先使用反向映射表
        String userId = sessionIdToUserIdMap.remove(session.getId());
        if (userId == null) {
            // 这个 session 可能从未成功绑定过用户，或者已经被清理过了
            log.warn("Session {} is not bound to any user.");
            return;
        }
        // 根据配置决定从哪个 Map 中移除
        if (config.getSession().isMultiLogin()) {
            // 多端登录清理逻辑
            CopyOnWriteArrayList<AtomicIOSession> sessions = userIdToSessionsMap.get(userId);
            if (sessions != null) {
                if(sessions.remove(session)) {
                    log.info("Session {} for user {} unbound due to disconnect. Remaining sessions: {}.",
                            session.getId(), userId, sessions.size());
                }
                if (sessions.isEmpty()) {
                    userIdToSessionsMap.remove(userId);
                    log.info("All sessions for user {} are disconnected. User is now offline.", userId);
                }
            }
        } else {
            // 单点登录清理逻辑
            // 仅当 Map 中存储的确实是当前这个 session 时才移除，防止并发问题
            if (userIdToSessionMap.remove(userId, session)) {
                log.info("User {} (session {}) unbound due to disconnect.", userId, session.getId());
            }
        }
        // 自动让用户离开所有他加入的群组
        engine.getGroupManager().unbindGroupsForSession(session);
    }

    /**
     * 只在本地发送消息给用户
     * 这个方法由集群消息处理器调用
     * @param userId    用户 ID
     * @param message   消息
     */
    public boolean sendToUserLocally(String userId, AtomicIOMessage message) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");

        if (config.getSession().isMultiLogin()) {
            // 多端模式
            List<AtomicIOSession> sessions = userIdToSessionsMap.get(userId);
            if (sessions != null && !sessions.isEmpty()) {
                log.debug("Sending message to user {} on {} device(s).", userId, sessions.size());
                sessions.forEach(s -> {
                    if (s.isActive()) s.send(message);
                });
                return true;
            }
        } else {
            // 单点模式
            AtomicIOSession session = userIdToSessionMap.get(userId);
            if (session != null && session.isActive()) {
                session.send(message);
                log.debug("Sent message to user {} (session {})", userId, session.getId());
                return true;
            }
        }
        return false;
    }

    /**
     * 本地广播
     */
    public void broadcastLocally(AtomicIOMessage message) {
        Objects.requireNonNull(message, "Message cannot be null");
        log.debug("Broadcasting message {} locally...", message.getCommandId());

        // 根据配置选择正确的 Map 进行遍历
        if (config.getSession().isMultiLogin()) {
            userIdToSessionsMap.values().forEach(sessions -> sessions.forEach(session -> {
                if (session.isActive()) {
                    session.send(message);
                }
            }));
        } else {
            userIdToSessionMap.values().forEach(session -> {
                if (session.isActive()) {
                    session.send(message);
                }
            });
        }
    }

    /**
     * 根据 userId 查找 session(s)
     * @param userId
     * @return
     */
    public List<AtomicIOSession> findSessionsByUserId(String userId) {
        if (config.getSession().isMultiLogin()) {
            return userIdToSessionsMap.getOrDefault(userId, new CopyOnWriteArrayList<>());
        } else {
            AtomicIOSession session = userIdToSessionMap.get(userId);
            return session != null ? List.of(session) : List.of();
        }
    }
}
