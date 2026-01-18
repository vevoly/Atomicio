package io.github.vevoly.atomicio.server.api.manager;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;

/**
 * 群组管理器接口
 * 负责物理连接，管理本节点的 Channel 组逻辑
 *
 * @since 0.6.5
 * @author vevoly
 */
public interface GroupManager {

    /**
     * 将 Session 加入本地物理组
     * @param groupId 组ID
     * @param session 会话对象
     */
    void joinLocal(String groupId, AtomicIOSession session);

    /**
     * 将 Session 从本地物理组移除
     * @param groupId 组ID
     * @param session 会话对象
     */
    void leaveLocal(String groupId, AtomicIOSession session);

    /**
     * 当 Session 断开时，自动清理其加入的所有本地组引用
     * @param session 断开的会话
     */
    void unbindGroupsForSession(AtomicIOSession session);

    /**
     * 向本地组分发消息
     * @param groupId 目标组ID
     * @param message 消息对象
     * @param excludeUserIds 需要排除的用户ID列表
     */
    void sendToGroupLocally(String groupId, Object message, String... excludeUserIds);

    /**
     * 高性能本地组广播（无过滤）
     * @param groupId 目标组ID
     * @param message 消息对象
     */
    void broadcastLocally(String groupId, Object message);
}
