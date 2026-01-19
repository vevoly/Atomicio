package io.github.vevoly.atomicio.core.manager;

import io.github.vevoly.atomicio.core.handler.NettyEventTranslationHandler;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.server.api.manager.GroupManager;
import io.github.vevoly.atomicio.server.api.manager.SessionManager;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群组管理器
 * 物理连接管理
 *
 * @since 0.5.8
 * @author vevoly
 *
 */
@Slf4j
public class AtomicIOGroupManager implements GroupManager {

    private final SessionManager sessionManager;

    // 物理组：GroupId -> Netty ChannelGroup
    private final Map<String, ChannelGroup> localGroups = new ConcurrentHashMap<>();

    public AtomicIOGroupManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 将 Session 对应的物理 Channel 加入本地组
     */
    @Override
    public void joinLocal(String groupId, AtomicIOSession session) {
        if (session instanceof NettySession) {
            ChannelGroup group = localGroups.computeIfAbsent(groupId,
                    k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
            group.add(((NettySession) session).getNettyChannel());
            log.debug("GroupManager: Session {} 加入本地物理组 {}", session.getId(), groupId);
        }
    }

    /**
     * 物理退出
     */
    @Override
    public void leaveLocal(String groupId, AtomicIOSession session) {
        ChannelGroup group = localGroups.get(groupId);
        if (group != null && session instanceof NettySession) {
            group.remove(((NettySession) session).getNettyChannel());
            if (group.isEmpty()) localGroups.remove(groupId);
        }
    }

    /**
     * 当一个 session 断开时，物理上从它加入的所有本地群组中移除。
     * @param session 断开的 session
     */
    @Override
    public void unbindGroupsForSession(AtomicIOSession session) {
        // 从 Session 属性中获取该连接加入的所有群组 ID 集合
        Set<String> joinedGroupIds = session.getAttribute(AtomicIOSessionAttributes.GROUPS);
        if (joinedGroupIds != null && !joinedGroupIds.isEmpty()) {
            log.info("GroupManager: Session {} 正在退出所有本地物理组 ({})", session.getId(), joinedGroupIds.size());
            // 遍历并移除物理 Channel 引用
            for (String groupId : joinedGroupIds) {
                leaveLocal(groupId, session);
            }
            // 清理 Session 上的属性引用
            session.setAttribute(AtomicIOSessionAttributes.GROUPS, null);
        }
    }

    /**
     * 本地组发消息
     * @param groupId        目标群组ID
     * @param message        要发送的对象（可以是 AtomicIOMessage，也可以是 ByteBuf）
     * @param excludeUserIds 需要排除的用户ID数组
     */
    @Override
    public void sendToGroupLocally(String groupId, Object message, Set<String> excludeUserIds) {
        ChannelGroup group = localGroups.get(groupId);
        if (group == null || group.isEmpty()) {
            return;
        }

        if (excludeUserIds != null && excludeUserIds.size() > 0) {
            // 策略 A: 有排除名单，需遍历组内 Channel 进行过滤发送
            group.forEach(channel -> {
                AtomicIOSession session = sessionManager.getLocalSessionById(channel.id().asLongText());
                if (session != null) {
                    String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
                    if (userId != null && !excludeUserIds.contains(userId)) {
                        session.send(message);
                    }
                }
            });
        } else {
            // 策略 B: 无排除名单，直接调用 ChannelGroup.writeAndFlush，性能最高（Netty 内部批量分发）
            group.writeAndFlush(message);
        }
        log.debug("GroupManager: 本地群组 {} 消息分发完成", groupId);
    }

    /**
     * 本地广播：最高性能的批量发送
     * 这里的 message 同样使用 Object，支持预编码后的字节流
     */
    @Override
    public void broadcastLocally(String groupId, Object message) {
        ChannelGroup group = localGroups.get(groupId);
        if (group != null && !group.isEmpty()) {
            group.writeAndFlush(message);
        }
    }
}
