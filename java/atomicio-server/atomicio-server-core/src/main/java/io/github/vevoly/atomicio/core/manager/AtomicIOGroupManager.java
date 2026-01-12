package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.core.handler.NettyEventTranslationHandler;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 群组管理器
 *
 * @since 0.5.8
 * @author vevoly
 *
 */
@Slf4j
public class AtomicIOGroupManager {

    private final Map<String, ChannelGroup> groups = new ConcurrentHashMap<>(); // Key: 组ID, Value: 组内的所有会话

    private final DefaultAtomicIOEngine engine;

    public AtomicIOGroupManager(DefaultAtomicIOEngine engine) {
        this.engine = engine;
    }

    public void joinGroup(String groupId, AtomicIOSession session) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(session, "Session cannot be null");

        // 安全检查
        if (!session.isActive()) {
            log.warn("Cannot join group {}: Session {} is not active.", groupId, session.getId());
            return;
        }
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        if (userId == null) {
            log.warn("Cannot join group {}: Session {} is not bound to a user.", groupId, session.getId());
            return;
        }
        // 获取或创建 ChannelGroup, GlobalEventExecutor.INSTANCE 确保 Group 资源的正确管理
        ChannelGroup group = groups.computeIfAbsent(groupId, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
        // 将 NettySession 内部的 Channel 加入到 ChannelGroup
        if (session instanceof NettySession) {
            group.add(((NettySession) session).getNettyChannel());
        } else {
            // 如果未来有其他 Session 实现，这里需要处理
            log.error("Unsupported session type for group operations: {}", session.getClass().getName());
            return;
        }
        // 将群组信息存储在 Session 属性中，方便后续清理
        Set<String> userGroups = session.getAttribute(AtomicIOSessionAttributes.GROUPS);
        if (userGroups == null) {
            userGroups = ConcurrentHashMap.newKeySet();
            session.setAttribute(AtomicIOSessionAttributes.GROUPS, userGroups);
        }
        // add 方法幂等，重复添加不会有问题
        boolean added = userGroups.add(groupId);
        if (added) {
            log.info("Session {} (user: {}) joined group {}. Current group size: {}",
                    session.getId(), userId, groupId, group.size());
        }
    }

    public void leaveGroup(String groupId, AtomicIOSession session) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(session, "Session cannot be null");

        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        ChannelGroup group = groups.get(groupId);
        if (group != null) {
            // 从 ChannelGroup 中移除
            if (session instanceof NettySession) {
                group.remove(((NettySession) session).getNettyChannel());
            }
            // 从 Session 属性中移除
            Set<String> userGroups = session.getAttribute(AtomicIOSessionAttributes.GROUPS);
            if (userGroups != null) {
                boolean removed = userGroups.remove(groupId);
                if(removed){
                    log.info("Session {} (user: {}) left group {}. Current group size: {}",
                            session.getId(), userId, groupId, group.size());
                }
            }
            // 如果群组为空，则清理
            if (group.isEmpty()) {
                groups.remove(groupId);
                log.info("Group {} is empty and has been removed.", groupId);
            }
        }
    }

    /**
     * 只在本地向群组发送消息
     * @param groupId        群组 ID
     * @param message        消息
     * @param excludeUserIds 排除的用户 ID 列表
     */
    public void sendToGroupLocally(String groupId, AtomicIOMessage message, String... excludeUserIds) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");

        ChannelGroup group = groups.get(groupId);
        if (group != null && !group.isEmpty()) {
            if (excludeUserIds != null && excludeUserIds.length > 0) {
                // 如果有排除的用户，需要逐个发送，并跳过排除者
                Set<String> excludeSet = Set.of(excludeUserIds);
                group.forEach(channel -> {
                    AtomicIOSession session = channel.attr(NettyEventTranslationHandler.SESSION_KEY).get();
                    if (session != null) {
                        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
                        if (userId != null && !excludeSet.contains(userId)) {
                            session.send(message);
                        }
                    }
                });
                log.debug("Sent message {} to group {} excluding {} users.", message.getCommandId(), groupId, excludeUserIds.length);
            } else {
                // 没有排除用户，直接调用 ChannelGroup 的批量发送，性能最高
                group.writeAndFlush(message);
                log.debug("Sent message {} to group {} (total {} sessions).", message.getCommandId(), groupId, group.size());
            }
        } else {
            log.warn("Group {} is empty or does not exist. Message {} not sent.", groupId, message.getCommandId());
        }
    }

    /**
     * 当一个 session 断开时，让它离开所有已加入的群组。
     * @param session 断开的 session
     */
    public void unbindGroupsForSession(AtomicIOSession session) {
        Set<String> userGroups = session.getAttribute(AtomicIOSessionAttributes.GROUPS);
        if (userGroups != null && !userGroups.isEmpty()) {
            log.info("Session {} is leaving all {} groups due to disconnect.", session.getId(), userGroups.size());
            // 创建副本以避免在遍历时修改
            for (String groupId : new CopyOnWriteArraySet<>(userGroups)) {
                leaveGroup(groupId, session);
            }
            session.setAttribute(AtomicIOSessionAttributes.GROUPS, null);
        }
    }
}
